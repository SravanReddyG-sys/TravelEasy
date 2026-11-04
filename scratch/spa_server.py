import http.server
import os
import socketserver

PORT = 4200
DIRECTORY = os.path.abspath('frontend/dist/traveleasy-frontend/browser')

class SPAServer(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def do_GET(self):
        # Serve static asset if file exists on disk; otherwise serve index.html for SPA router
        requested_path = self.translate_path(self.path)
        if not os.path.exists(requested_path) or os.path.isdir(requested_path):
            self.path = '/index.html'
        return super().do_GET()

socketserver.TCPServer.allow_reuse_address = True
with socketserver.TCPServer(('127.0.0.1', PORT), SPAServer) as httpd:
    print(f'SPA Server running at http://127.0.0.1:{PORT}')
    httpd.serve_forever()
