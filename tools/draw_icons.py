#!/usr/bin/env python3
"""Generate matching 24px Android vectors and an SVG sprite for the preview."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STROKE = {
    'moon': 'M20.8 15.4A8.6 8.6 0 0 1 8.6 3.2a8.7 8.7 0 1 0 12.2 12.2Z',
    'sun': 'M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8M12 2v2m0 16v2M2 12h2m16 0h2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4',
    'weather': 'M6.2 16.8A3.8 3.8 0 0 1 7 9.3a5.1 5.1 0 0 1 9.9 1.7h.7a2.9 2.9 0 1 1 0 5.8ZM14 4V2m4.3 3.8 1.4-1.4m.7 4.5h2',
    'cloud': 'M6.2 18A4.3 4.3 0 0 1 7 9.5a5.3 5.3 0 0 1 10.3 2.1h.5a3.2 3.2 0 1 1 0 6.4Z',
    'rain': 'M6.4 15.5A3.5 3.5 0 0 1 7 8.6a5.1 5.1 0 0 1 9.9 1.5h.7a2.7 2.7 0 1 1 0 5.4ZM8 19l-1 2m5-2-1 2m5-2-1 2',
    'headlights': 'M11 5.2H9.1a6.3 6.3 0 0 0 0 12.6H11ZM14.5 6l6.5 2.4m-6.5 2.4 6.5 2.4m-6.5 2.4 6.5 2.4',
    'bluetooth': 'M6 6.4 17.5 17 12 22V2l5.5 5L6 17.6',
    'navigation': 'M12 3l7.7 17.4L12 16l-7.7 4.4L12 3Z',
    'compass': 'M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18Zm3.4 5.6-1.8 5-5 1.8 1.8-5z',
    'music': 'M9 17V5l11-2v12M9 8l11-2M9 17c0 1.4-1.6 2.6-3.5 2.6S2 18.4 2 17s1.6-2.6 3.5-2.6S9 15.6 9 17Zm11-2c0 1.4-1.6 2.6-3.5 2.6S13 16.4 13 15s1.6-2.6 3.5-2.6S20 13.6 20 15Z',
    'phone': 'M7.5 3H5a2 2 0 0 0-2 2c0 8.8 7.2 16 16 16a2 2 0 0 0 2-2v-2.5l-5-2.2-1.8 2a13 13 0 0 1-6.5-6.5l2-1.8Z',
    'radio': 'M5 7l13-4M5 7h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2Zm1 4h12M9 14.6a2 2 0 1 0 0 4a2 2 0 1 0 0-4Zm5 1h4m-4 2.6h4',
    'settings': 'M12 8.5a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7Zm-1.4-6.3h2.8l.7 2.5 1.9.8 2.3-1.3 2 2-1.3 2.3.8 1.9 2.5.7v2.8l-2.5.7-.8 1.9 1.3 2.3-2 2-2.3-1.3-1.9.8-.7 2.5h-2.8l-.7-2.5-1.9-.8-2.3 1.3-2-2 1.3-2.3-.8-1.9-2.5-.7v-2.8l2.5-.7.8-1.9-1.3-2.3 2-2 2.3 1.3 1.9-.8z',
    'apps': 'M5 3h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm11 0h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2ZM5 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2Zm11 0h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2Z',
    'chevron': 'M9 5l7 7-7 7',
    'close': 'M6 6l12 12M18 6 6 18',
    'wifi': 'M3 8.9a14.2 14.2 0 0 1 18 0M6.2 12.3a9.2 9.2 0 0 1 11.6 0m-8.3 3.3a4 4 0 0 1 5 0M12 19h.01',
    'download': 'M12 3v12m-4-4 4 4 4-4M4 16v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3',
}
FILL = {
    'play': 'M8 4.7a1 1 0 0 0-1.5.9v12.8a1 1 0 0 0 1.5.9l10.2-6.4a1 1 0 0 0 0-1.8Z',
    'pause': 'M7 5h3a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Zm7 0h3a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z',
    'previous': 'M5 6h2v12H5ZM18.3 6.3v11.4L8.8 12Z',
    'next': 'M17 6h2v12h-2ZM5.7 6.3v11.4l9.5-5.7Z',
}

symbols = []
for name, path in (STROKE | FILL).items():
    stroke = name in STROKE
    attrs = ('android:fillColor="#00000000" android:strokeColor="#FFFFFF" '
             'android:strokeWidth="1.65" android:strokeLineCap="round" android:strokeLineJoin="round"'
             if stroke else 'android:fillColor="#FFFFFF"')
    if name not in {'navigation', 'chevron', 'close', 'wifi', 'download'}:
        (ROOT / f'app/src/main/res/drawable/ic_{name}.xml').write_text(
            '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" '
            'android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'
            f'    <path {attrs} android:pathData="{path}"/>\n</vector>\n')
    svg_attrs = ('fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" '
                 'stroke-linejoin="round"' if stroke else 'fill="currentColor"')
    symbols.append(f'  <symbol id="{name}" viewBox="0 0 24 24"><path {svg_attrs} d="{path}"/></symbol>')
# Extra browser controls. The warning also has a hand-tuned Android vector.
symbols += [
    '  <symbol id="warning" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="M10.6 4.1a1.6 1.6 0 0 1 2.8 0L22 18.8a1.62 1.62 0 0 1-1.43 2.45H3.43A1.62 1.62 0 0 1 2 18.8ZM12 8.4v5.15"/><circle cx="12" cy="16.9" r="1.12" fill="currentColor"/></symbol>',
    '  <symbol id="search" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" d="M17 10.5a6.5 6.5 0 1 1-13 0 6.5 6.5 0 0 1 13 0Zm-1.9 4.6L21 21"/></symbol>',
    '  <symbol id="back" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" d="m10 6-6 6 6 6m-6-6h16"/></symbol>',
]
(ROOT / 'preview/icons.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg">\n' + '\n'.join(symbols) + '\n</svg>\n')
