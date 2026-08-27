#!/usr/bin/env python3
"""Checks the wiring invariants of the Trackly pipeline. See action.yml for why this is a container."""

import os
import re
import sys
from pathlib import Path

import yaml

WORKFLOWS = Path(".github/workflows")
ACTIONS = Path(".github/actions")
violations: list[str] = []


def fail(where: str, message: str) -> None:
    violations.append(f"`{where}` — {message}")
    print(f"::error file={where}::{message}")


def load(path: Path) -> dict:
    return yaml.safe_load(path.read_text()) or {}


def is_parent_pom(pom: Path) -> bool:
    return "<packaging>pom</packaging>" in pom.read_text()


def inherited_poms(pom: Path) -> list[Path]:
    """Every POM in this repository that `pom` inherits from, nearest first."""
    chain: list[Path] = []
    seen = {pom.resolve()}
    text = pom.read_text()
    while match := re.search(r"<relativePath>([^<]+)</relativePath>", text):
        parent = (pom.parent / match.group(1).strip()).resolve()
        if parent.is_dir():
            parent = parent / "pom.xml"
        if not parent.is_file() or parent in seen:
            break
        seen.add(parent)
        chain.append(parent)
        pom, text = parent, parent.read_text()
    return chain


def plugin_block(pom: str, artifact: str) -> str | None:
    """The `<plugin>` element declaring `artifact`, nearest declaration first."""
    for block in re.findall(r"<plugin>.*?</plugin>", pom, re.DOTALL):
        if f"<artifactId>{artifact}</artifactId>" in block:
            return block
    return None


def discover_services(root: Path) -> list[str]:
    return sorted(
        p.name
        for p in root.iterdir()
        if p.is_dir() and (p / "pom.xml").is_file() and not is_parent_pom(p / "pom.xml")
    )


def paths_filters() -> str:
    ci = load(WORKFLOWS / "ci.yaml")
    for job in ci.get("jobs", {}).values():
        for step in job.get("steps", []) or []:
            if "paths-filter" in str(step.get("uses", "")):
                return step.get("with", {}).get("filters", "")
    return ""


def check_service_layout(service: str, min_coverage: float) -> None:
    directory = Path(service)

    for required in ("Dockerfile", "mvnw", "pom.xml"):
        if not (directory / required).is_file():
            fail(f"{service}/{required}", f"{service} is a service but has no {required}")

    own = directory / "pom.xml"
    chain = [own, *inherited_poms(own)]
    pom = "\n".join(path.read_text() for path in chain)

    check_style_is_gated(service, chain)

    if "jacoco-maven-plugin" not in pom:
        fail(f"{service}/pom.xml", "no jacoco-maven-plugin: this service's coverage is reported, not gated")
        return

    minima = [float(m) for m in re.findall(r"<minimum>([\d.]+)</minimum>", pom)]
    if not minima:
        fail(f"{service}/pom.xml", "jacoco-maven-plugin declares no <minimum>, so the check goal gates nothing")
    elif min(minima) < min_coverage:
        fail(
            f"{service}/pom.xml",
            f"weakest coverage minimum is {min(minima)}, below the required {min_coverage}",
        )

    uses_testcontainers = any(
        "testcontainers" in path.read_text().lower()
        for path in (directory / "src" / "test").rglob("*.java")
    ) if (directory / "src" / "test").is_dir() else False

    if uses_testcontainers and not (directory / ".ci" / "testcontainers-images.txt").is_file():
        fail(
            f"{service}/.ci/testcontainers-images.txt",
            "integration tests start Testcontainers but the image list the integration stage pre-pulls is missing",
        )


def check_style_is_gated(service: str, chain: list[Path]) -> None:
    """Checkstyle is the first gate `./mvnw package` reaches, and every way of weakening it is silent."""
    pom = "\n".join(path.read_text() for path in chain)
    plugin = plugin_block(pom, "maven-checkstyle-plugin")

    if plugin is None:
        fail(f"{service}/pom.xml", "no maven-checkstyle-plugin: this service's style is reported, not gated")
        return

    if "<goal>check</goal>" not in plugin:
        fail(
            f"{service}/pom.xml",
            "maven-checkstyle-plugin binds no check goal, so it can only report style and never fail on it",
        )

    if "<violationSeverity>error</violationSeverity>" not in plugin:
        fail(
            f"{service}/pom.xml",
            "maven-checkstyle-plugin does not set <violationSeverity>error</violationSeverity>, "
            "so violations under that severity leave the build green",
        )

    location = re.search(r"<configLocation>([^<]+)</configLocation>", plugin)
    config = Path(location.group(1).replace("${project.basedir}", service)) if location else None
    if config is None or not config.is_file():
        fail(f"{service}/pom.xml", "maven-checkstyle-plugin names no readable <configLocation> ruleset")
        return

    expansion = " ".join(re.findall(r"<propertyExpansion>([^<]+)</propertyExpansion>", plugin))
    for deferred in re.findall(r'name="severity"\s+value="\$\{([^}]+)\}"', config.read_text()):
        if f"{deferred}=error" not in expansion:
            fail(
                f"{service}/pom.xml",
                f"{config} defers its severity to ${{{deferred}}} and no <propertyExpansion> sets it to error, "
                "so every rule degrades to a warning and the gate passes on any violation",
            )


def check_service_is_wired(service: str) -> None:
    filters = paths_filters()
    if service not in filters:
        fail(".github/workflows/ci.yaml", f"{service} has no path filter, so a change to it builds nothing")

    selector = (Path(".github/scripts/select-services.sh")).read_text()
    if service not in selector:
        fail(
            ".github/scripts/select-services.sh",
            f"{service} is missing from the service list, so a shared change will not rebuild it",
        )

    deploy = (WORKFLOWS / "deploy.yaml").read_text()
    if service not in deploy:
        fail(".github/workflows/deploy.yaml", f"{service} is built but never deployed")

    dependabot = load(Path(".github/dependabot.yml"))
    for ecosystem in ("maven", "docker"):
        wired = any(
            entry.get("package-ecosystem") == ecosystem and entry.get("directory") == f"/{service}"
            for entry in dependabot.get("updates", [])
        )
        if not wired:
            fail(
                ".github/dependabot.yml",
                f"{service} has no {ecosystem} entry, so its {ecosystem} dependencies are never updated",
            )


def check_inherited_poms_are_wired(services: list[str]) -> None:
    """A POM several services inherit is part of all their builds, so a change to it must rebuild all of them."""
    filters = paths_filters()
    parents = {parent for service in services for parent in inherited_poms(Path(service) / "pom.xml")}
    for parent in sorted(parents):
        directory = os.path.relpath(parent.parent)
        if directory not in filters:
            fail(
                ".github/workflows/ci.yaml",
                f"services inherit {directory}/pom.xml but it has no path filter, "
                "so a change to the shared build rebuilds nothing",
            )


def check_workflow_hygiene() -> None:
    for path in sorted(WORKFLOWS.glob("*.yaml")):
        workflow = load(path)
        top_level_permissions = "permissions" in workflow
        reusable = "workflow_call" in (workflow.get(True) or workflow.get("on") or {})

        if not top_level_permissions and not reusable:
            fail(str(path), "no top-level permissions block, so GITHUB_TOKEN keeps the repository default")

        for name, job in workflow.get("jobs", {}).items():
            if "uses" in job:
                continue
            if "timeout-minutes" not in job:
                fail(str(path), f"job `{name}` has no timeout-minutes and can hang for six hours")
            if not top_level_permissions and "permissions" not in job:
                fail(str(path), f"job `{name}` neither inherits nor declares permissions")


def check_actions_are_pinned() -> None:
    """Third-party actions must be pinned to a commit, first-party ones to a tag (see .github/zizmor.yml)."""
    for path in sorted(list(WORKFLOWS.glob("*.yaml")) + list(ACTIONS.glob("*/action.yml"))):
        for line, text in enumerate(path.read_text().splitlines(), start=1):
            match = re.search(r"uses:\s*([^\s'\"]+)", text)
            if not match:
                continue
            reference = match.group(1)
            if reference.startswith("./") or reference.startswith("docker://"):
                continue
            owner = reference.split("/")[0]
            if owner in ("actions", "github"):
                continue
            if "@" not in reference or not re.fullmatch(r"[0-9a-f]{40}", reference.split("@")[-1]):
                fail(f"{path}:{line}", f"third-party action `{reference}` is not pinned to a full commit SHA")


def summarise(services: list[str]) -> None:
    lines = [
        "## Pipeline conventions",
        "",
        f"Services checked: {', '.join(f'`{s}`' for s in services)}",
        "",
    ]
    if violations:
        lines += [f"**{len(violations)} violation(s):**", ""] + [f"- {v}" for v in violations]
    else:
        lines.append("Every service is wired into change detection, the deploy, Dependabot, a style gate and a "
                     "coverage gate, and every job is bounded by a timeout and a permissions block.")
    lines.append("")

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as handle:
            handle.write("\n".join(lines))
    print("\n".join(lines))

    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a") as handle:
            handle.write(f"services={' '.join(services)}\nviolations={len(violations)}\n")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    min_coverage = float(sys.argv[2] if len(sys.argv) > 2 else "0.85")

    services = discover_services(root)
    if not services:
        print(f"::error::no service (a directory holding a pom.xml) found under {root}")
        return 1

    for service in services:
        check_service_layout(service, min_coverage)
        check_service_is_wired(service)

    check_inherited_poms_are_wired(services)
    check_workflow_hygiene()
    check_actions_are_pinned()
    summarise(services)

    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main())
