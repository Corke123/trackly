import { EnvironmentProviders, provideEnvironmentInitializer, inject } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';

/**
 * The icons are registered as literals rather than pulled from a font CDN: the gateway serves the
 * SPA as the single public entry point (ADR 0006), and an icon set that needs a second origin turns
 * every button into the word "more_vert" the moment that origin is unreachable.
 */
const ICON_PATHS: Readonly<Record<string, string>> = {
  add: 'M11 13H5v-2h6V5h2v6h6v2h-6v6h-2z',
  edit: 'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z',
  delete: 'M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
  more_vert:
    'M12 8a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm0 2a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm0 6a2 2 0 1 0 0 4 2 2 0 0 0 0-4z',
  drag_indicator:
    'M9 20a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm6 0a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM9 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm6 0a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM9 8a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm6 0a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
  dark_mode:
    'M12 3a9 9 0 1 0 9 9c0-.46-.04-.91-.1-1.36A5.39 5.39 0 0 1 16.5 13a5.4 5.4 0 0 1-5.4-5.4c0-1.81.89-3.41 2.26-4.4-.44-.13-.9-.2-1.36-.2z',
  light_mode:
    'M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10zm0-5 2 3h-4l2-3zm0 20-2-3h4l-2 3zM2 12l3-2v4l-3-2zm20 0-3 2v-4l3 2zM4.93 4.93l3.54 1.41-2.12 2.13-1.42-3.54zm14.14 14.14-3.54-1.41 2.12-2.13 1.42 3.54zM4.93 19.07l1.42-3.54 2.12 2.13-3.54 1.41zM19.07 4.93l-1.42 3.54-2.12-2.13 3.54-1.41z',
  logout:
    'M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5-5-5zM4 5h8V3H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h8v-2H4V5z',
  account_circle:
    'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 3a3 3 0 1 1 0 6 3 3 0 0 1 0-6zm0 14.2a7.2 7.2 0 0 1-6-3.22c.03-1.99 4-3.08 6-3.08s5.97 1.09 6 3.08a7.2 7.2 0 0 1-6 3.22z',
  chevron_left: 'M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z',
  chevron_right: 'M10 6 8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z',
  swap_horiz: 'M6.99 11 3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z',
  comment:
    'M20 2H4a2 2 0 0 0-2 2v18l4-4h14a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z',
  person: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z',
};

export function provideTracklyIcons(): EnvironmentProviders {
  return provideEnvironmentInitializer(() => {
    const registry = inject(MatIconRegistry);
    const sanitizer = inject(DomSanitizer);

    for (const [name, path] of Object.entries(ICON_PATHS)) {
      registry.addSvgIconLiteral(
        name,
        sanitizer.bypassSecurityTrustHtml(
          `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="${path}"/></svg>`,
        ),
      );
    }

    registry.setDefaultFontSetClass('material-symbols-outlined');
  });
}
