#!/usr/bin/env python3
"""Serve the browser simulator, shared artwork and (when built) the installable APK."""
import argparse
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
APK = 'Vezel-Drive-Console.apk'
DOWNLOADS = {f'download/{name}': name for name in (APK, f'{APK}.sha256')}


class Preview(SimpleHTTPRequestHandler):
    extensions_map = {**SimpleHTTPRequestHandler.extensions_map,
                      '.apk': 'application/vnd.android.package-archive',
                      '.sha256': 'text/plain; charset=utf-8'}

    def translate_path(self, path):
        path = unquote(urlsplit(path).path).strip('/')
        folder = ROOT / 'preview'
        if path.startswith('shared/'):
            folder, path = ROOT / 'shared', path.removeprefix('shared/')
        elif path in DOWNLOADS:
            folder, path = ROOT / 'artifacts', DOWNLOADS[path]
        target = (folder / (path or 'index.html')).resolve()
        if not target.is_relative_to(folder) or not target.is_file():
            return str(ROOT / 'preview' / '.not-found')
        return str(target)

    def end_headers(self):
        self.send_header('X-Content-Type-Options', 'nosniff')
        download = DOWNLOADS.get(urlsplit(self.path).path.lstrip('/'))
        self.send_header('Cache-Control', 'no-store' if download else 'no-cache')
        if download:
            self.send_header('Content-Disposition', f'attachment; filename="{download}"')
        super().end_headers()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=3000)
    args = parser.parse_args()
    with ThreadingHTTPServer(('0.0.0.0', args.port), Preview) as server:
        print(f'Vezel Drive Console preview on port {args.port}', flush=True)
        server.serve_forever()
