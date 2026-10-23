#!/usr/bin/env python3
"""Real generated media and a loopback HTTP/Range/HLS/DASH server for JVM tests."""
import functools,http.server,json,re,socket,subprocess,sys,tempfile,threading,time
from pathlib import Path
class Handler(http.server.SimpleHTTPRequestHandler):
 requests={}; ranges=0; active=0; peak=0; lock=threading.Lock()
 def log_message(self,*args): pass
 def do_HEAD(self):
  if self.path.split('?')[0] in ['/transient.mp4','/resume.mp4']:self.path='/copy.mp4'
  super().do_HEAD()
 def do_GET(self):
  name=self.path.split('?')[0]
  with self.lock:self.requests[name]=self.requests.get(name,0)+1;request=self.requests[name]
  if name.startswith('/missing') or name=='/broken-001.ts':self.send_error(404);return
  if name=='/transient.mp4' and request==1:self.send_error(503);return
  if name in ['/transient.mp4','/resume.mp4']:self.path='/copy.mp4'
  target=Path(self.translate_path(self.path))
  if not target.is_file():return super().do_GET()
  if name.endswith('.ts'):
   with self.lock:type(self).active+=1;type(self).peak=max(type(self).peak,type(self).active)
   try:time.sleep(.08);return super().do_GET()
   finally:
    with self.lock:type(self).active-=1
  size=target.stat().st_size;start=0
  match=re.fullmatch(r'bytes=(\d+)-',self.headers.get('Range',''))
  if match:
   start=int(match[1]);type(self).ranges+=1
   if start>=size:self.send_response(416);self.send_header('Content-Range',f'bytes */{size}');self.end_headers();return
  self.send_response(206 if match else 200)
  self.send_header('Content-Type',self.guess_type(str(target)));self.send_header('Content-Length',str(size-start));self.send_header('Accept-Ranges','bytes')
  if match:self.send_header('Content-Range',f'bytes {start}-{size-1}/{size}')
  self.end_headers()
  try:
   with target.open('rb') as src:
    src.seek(start)
    if name=='/resume.mp4' and request==1 and not match:
     self.wfile.write(src.read(size//2));self.wfile.flush();self.connection.shutdown(socket.SHUT_RDWR);self.connection.close();return
    while chunk:=src.read(16*1024):
     self.wfile.write(chunk)
     if name=='/slow.mp4':self.wfile.flush();time.sleep(.03)
  except (BrokenPipeError,ConnectionResetError):pass

def run(*cmd):
 r=subprocess.run(list(map(str,cmd)),capture_output=True,text=True,timeout=120)
 if r.returncode:raise RuntimeError(r.stderr[-2000:])
def main():
 with tempfile.TemporaryDirectory(prefix='ytdlpg-fixture-') as directory:
  root=Path(directory)
  run('ffmpeg','-v','error','-f','lavfi','-i','testsrc2=s=320x180:r=15:d=3','-f','lavfi','-i','sine=frequency=440:duration=3','-c:v','libx264','-c:a','aac','-shortest',root/'copy.mp4')
  run('ffmpeg','-v','error','-i',root/'copy.mp4','-c:v','libvpx-vp9','-crf','30','-b:v','0','-c:a','libopus',root/'convert.webm')
  run('ffmpeg','-v','error','-i',root/'convert.webm','-c','copy',root/'convertmp4.mp4')
  run('ffmpeg','-v','error','-i',root/'copy.mp4','-c','copy','-f','dash','-seg_duration','1','-adaptation_sets','id=0,streams=v id=1,streams=a',root/'split.mpd')
  run('ffmpeg','-v','error','-i',root/'copy.mp4','-c:v','libx264','-c:a','aac','-g','5','-hls_time','0.3','-hls_list_size','0','-hls_segment_filename',root/'fragment-%03d.ts',root/'hls.m3u8')
  for name,suffix in [('copy','mp4'),('convert','webm'),('convertmp4','mp4'),('resume','mp4'),('transient','mp4')]:
   (root/f'{name}.html').write_text(f'<html><title>{name}</title><video><source src="/{name}.{suffix}" type="video/{suffix}" width="320" height="180"></video></html>')
  (root/'broken-master.m3u8').write_text('#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=400000,RESOLUTION=320x180\nbroken.m3u8\n')
  (root/'broken.m3u8').write_text((root/'hls.m3u8').read_text().replace('fragment-001.ts','broken-001.ts'))
  (root/'slow.mp4').write_bytes((root/'copy.mp4').read_bytes())
  (root/'corrupt.mp4').write_bytes(b'not valid media\0'*200)
  server=http.server.ThreadingHTTPServer(('127.0.0.1',0),functools.partial(Handler,directory=directory))
  threading.Thread(target=server.serve_forever,daemon=True).start()
  print(json.dumps({'base':f'http://127.0.0.1:{server.server_port}','root':str(root)}),flush=True)
  try:
   for line in sys.stdin:
    if line.strip()=='stats':print(json.dumps({'ranges':Handler.ranges,'peak_fragments':Handler.peak,'requests':Handler.requests}),flush=True)
    if line.strip()=='stop':break
  finally:server.shutdown();server.server_close()
if __name__=='__main__':main()
