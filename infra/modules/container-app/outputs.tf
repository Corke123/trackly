output "name" {
  value = azurerm_container_app.this.name
}

output "fqdn" {
  description = "Empty for internal apps, which have no externally resolvable name."
  value       = try(azurerm_container_app.this.ingress[0].fqdn, "")
}

output "latest_revision_name" {
  value = azurerm_container_app.this.latest_revision_name
}
