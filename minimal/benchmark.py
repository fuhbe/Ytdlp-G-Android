#!/usr/bin/env python3
"""Paired reproducible host measurements; never a YouTube/Snaptube benchmark."""
import argparse,functools,http.server,json,os,resource,shutil,subprocess,sys,tempfile,threading,time,hashlib,platform
from pathlib import Path
HERE=Path(__file__).resolve().parent
ENGINE=HERE/'app/build/generated/runtime/res/raw/ytdlp'
FIELDS='%(progress.{downloaded_bytes,total_bytes,total_bytes_estimate,speed,eta,status,elapsed,fragment_index,fragment_count})j'
class Handler(http.server.SimpleHTTPRequestHandler):
 def do_GET(self):
  if self.path.endswith('.ts'): time.sleep(.08) # Explicit controlled per-request latency, not Internet throughput.
  super().do_GET()
 def log_message(self,*args): pass

def run(*cmd): return subprocess.run(list(map(str,cmd)),check=True,capture_output=True,text=True,timeout=150).stdout

def measure(command,root,metadata_seconds,mode):
 start=time.perf_counter(); first=None; peak=0.; speeds=[]; transferred=0; last_download=None; rss=0; average_transfer=None
 before=resource.getrusage(resource.RUSAGE_CHILDREN)
 p=subprocess.Popen(list(map(str,command)),stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
 log=[]; stop=threading.Event()
 def memory():
  nonlocal rss
  while not stop.wait(.05):
   try:
    parents={}; resident={}
    for d in Path('/proc').iterdir():
     if not d.name.isdigit(): continue
     try:
      stat=(d/'stat').read_text().split(')')[-1].split();parents[int(d.name)]=int(stat[1])
      status=(d/'status').read_text();resident[int(d.name)]=int(next(x.split()[1] for x in status.splitlines() if x.startswith('VmRSS:')))*1024
     except (OSError,StopIteration,ValueError): pass
    family={p.pid}
    for _ in range(8): family|={pid for pid,parent in parents.items() if parent in family}
    rss=max(rss,sum(resident.get(pid,0) for pid in family))
   except OSError: pass
 threading.Thread(target=memory,daemon=True).start()
 try:
  for line in p.stdout:
   log.append(line)
   if line.startswith('YG:'):
    data=json.loads(line[3:]); n=data.get('downloaded_bytes') or 0
    if n and first is None: first=time.perf_counter()-start
    transferred=max(transferred,n)
    if isinstance(data.get('speed'),(int,float)):speeds.append(data['speed']);peak=max(peak,data['speed'])
    if data.get('status')=='finished':
     last_download=time.perf_counter()-start
     if data.get('elapsed'): average_transfer=n/data['elapsed']
  assert p.wait(timeout=150)==0,''.join(log)[-2000:]
 finally: stop.set()
 elapsed=time.perf_counter()-start
 after=resource.getrusage(resource.RUSAGE_CHILDREN)
 file=root/('output.mp3' if '--audio-format' in command else 'output.mp4'); assert file.is_file(); validation_start=time.perf_counter()
 probe=json.loads(run('ffprobe','-v','error',*(['-count_packets'] if mode=='optimized' else []),'-show_streams','-show_format','-of','json',file))
 validation=time.perf_counter()-validation_start if mode=='optimized' else 0
 if '--audio-format' in command:assert any(s['codec_name']=='mp3' for s in probe['streams'])
 else:assert {s['codec_name'] for s in probe['streams'] if s['codec_type'] in ['audio','video']}=={'h264','aac'}
 # Read/decode real media, not only its container header.
 run('ffmpeg','-v','error','-i',file,'-f','null','-')
 save=time.perf_counter(); exported=root/'saved'/file.name;exported.parent.mkdir(exist_ok=True)
 with file.open('rb') as src,exported.open('wb') as dst: shutil.copyfileobj(src,dst,64*1024)
 export=time.perf_counter()-save
 return {'analysis_seconds':metadata_seconds,'first_transfer_seconds':first,'transfer_bytes':transferred,
  'average_transfer_bytes_per_second':average_transfer,'average_callback_bytes_per_second':sum(speeds)/len(speeds) if speeds else None,'peak_callback_bytes_per_second':peak or None,
  'transfer_finished_seconds':last_download,'download_and_postprocess_seconds':elapsed,'postprocess_seconds':max(0,elapsed-last_download) if last_download else None,
  'validation_seconds':validation,'export_seconds':export,'total_seconds':metadata_seconds+elapsed+validation+export,'cpu_seconds':after.ru_utime+after.ru_stime-before.ru_utime-before.ru_stime,
  'sampled_process_family_peak_rss_bytes':rss,'output_bytes':file.stat().st_size,'streams':[{'type':s['codec_type'],'codec':s['codec_name'],'height':s.get('height')} for s in probe['streams']],
  'decode':'PASS','success':True}

def main(mode,repetitions):
 results=[]
 with tempfile.TemporaryDirectory(prefix='ytdlpg-benchmark-') as directory:
  root=Path(directory)
  run('ffmpeg','-v','error','-f','lavfi','-i','testsrc2=s=640x360:r=24:d=6','-f','lavfi','-i','sine=frequency=440:duration=6','-c:v','libx264','-c:a','aac','-shortest',root/'copy.mp4')
  run('ffmpeg','-v','error','-i',root/'copy.mp4','-c:v','libvpx-vp9','-b:v','0','-crf','30','-c:a','libopus',root/'convert.webm')
   # Create 12 independently decodable segments for a meaningful N=1/N=4 test.
  run('ffmpeg','-v','error','-i',root/'copy.mp4','-c:v','libx264','-c:a','aac','-g','12','-hls_time','0.5','-hls_list_size','0','-hls_segment_filename',root/'fragment-%02d.ts',root/'fragments.m3u8')
  plugins=HERE/'plugins'
  if mode=='baseline':
   plugins=root/'baseline-plugins';target=plugins/'glassmp4/yt_dlp_plugins/postprocessor/glassmp4.py';target.parent.mkdir(parents=True);shutil.copy2(HERE/'benchmarks/baseline_glassmp4.py',target)
  server=http.server.ThreadingHTTPServer(('127.0.0.1',0),functools.partial(Handler,directory=directory));threading.Thread(target=server.serve_forever,daemon=True).start()
  try:
   for repetition in range(repetitions):
    for case,source,mp3,fragments in [('mp3','copy.mp4',True,1),('compatible-mp4','copy.mp4',False,1),('convert-mp4','convert.webm',False,1),('hls-N1','fragments.m3u8',False,1),('hls-N4','fragments.m3u8',False,4)]:
     target=root/f'{case}-{repetition}';target.mkdir();url=f'http://127.0.0.1:{server.server_port}/{source}'
     t=time.perf_counter();run(sys.executable,ENGINE,'--ignore-config','--skip-download','--dump-single-json',url);metadata=time.perf_counter()-t
     command=[sys.executable,ENGINE,'--ignore-config','--no-simulate','--newline','--progress','--progress-delta','.1','--progress-template','download:YG:'+FIELDS,'--concurrent-fragments',str(fragments),'--abort-on-unavailable-fragments','-o',target/'output.%(ext)s']
     if mode=='optimized':command+=['--keep-video','--postprocessor-args',f"ffmpeg:-progress '{target / 'ffmpeg.progress'}' -stats_period 1 -nostats -nostdin"+(' -threads 1' if mp3 else '')]
     if mp3:command+=['-f','ba/b','-x','--audio-format','mp3','--audio-quality','0']
     else:command+=['--plugin-dirs',plugins,'--use-postprocessor','GlassMP4:preset='+('fast' if mode=='baseline' else 'veryfast'),'-f','b']
     result=measure(command+[url],target,metadata,mode);result.update(case=case,repetition=repetition);results.append(result);print(mode,case,round(result['total_seconds'],3),'seconds',flush=True)
  finally:server.shutdown();server.server_close()
 report={'engine_sha256':hashlib.sha256(ENGINE.read_bytes()).hexdigest(),'host_machine':platform.machine(),'ffmpeg':run('ffmpeg','-version').splitlines()[0],'environment':'host Linux HTTP loopback; generated 360p/24fps/6s media; segment request latency 80ms; not Android/YouTube/Snaptube','mode':mode,'repetitions':repetitions,'measurements':results,'apk_baseline_bytes':56190405,'conclusions':'Compare paired cases, not unrelated networks or source qualities.'}
 out=HERE/f'build/benchmark-{mode}.json';out.write_text(json.dumps(report,indent=2));print(out)
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--mode',choices=['baseline','optimized'],required=True);p.add_argument('--repetitions',type=int,default=3);a=p.parse_args();main(a.mode,a.repetitions)
