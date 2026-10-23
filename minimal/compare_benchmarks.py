#!/usr/bin/env python3
"""Report observed medians, including regressions; no speed assertions or marketing."""
import json,statistics
from pathlib import Path
HERE=Path(__file__).resolve().parent
baseline=json.loads((HERE/'build/benchmark-baseline.json').read_text())
optimized=json.loads((HERE/'build/benchmark-optimized.json').read_text())
def median(report,case,key):
 values=[r[key] for r in report['measurements'] if r['case']==case and r.get(key) is not None]
 return statistics.median(values) if values else None
assert baseline['repetitions']==optimized['repetitions']
assert baseline.get('engine_sha256')==optimized.get('engine_sha256')
rows=[]
for case in ['mp3','compatible-mp4','convert-mp4','hls-N1','hls-N4']:
 before=median(baseline,case,'total_seconds');after=median(optimized,case,'total_seconds')
 rows.append({'case':case,'baseline_median_total_seconds':before,'optimized_median_total_seconds':after,
  'observed_total_change_percent':(after/before-1)*100,'optimized_cpu_seconds':median(optimized,case,'cpu_seconds'),
  'optimized_sampled_peak_rss_bytes':median(optimized,case,'sampled_process_family_peak_rss_bytes'),
  'optimized_first_transfer_seconds':median(optimized,case,'first_transfer_seconds'),
  'optimized_average_transfer_bytes_per_second':median(optimized,case,'average_transfer_bytes_per_second'),
  'optimized_peak_callback_bytes_per_second':median(optimized,case,'peak_callback_bytes_per_second'),
  'output_bytes_baseline':median(baseline,case,'output_bytes'),'output_bytes_optimized':median(optimized,case,'output_bytes')})
n1=median(optimized,'hls-N1','total_seconds');n4=median(optimized,'hls-N4','total_seconds')
result={'scope':optimized['environment'],'repetitions':optimized['repetitions'],'youtube_test':False,'android_test':False,'snaptube_comparison':False,
 'rows':rows,'controlled_hls_N1_to_N4_total_change_percent':(n4/n1-1)*100,
 'notes':['CPU covers host download/postprocess subprocesses, not the phone or analysis process.','RSS is sampled for the host process family; it is approximate.','Streaming export is a local host copy, not Android SAF.','80ms segment latency is deliberately controlled, not measured Internet latency.','veryfast at CRF18 retains resolution/codecs; encoded size and compression efficiency may change.']}
(HERE/'build/benchmark-comparison.json').write_text(json.dumps(result,indent=2)+'\n')
lines=['# Generated-media host measurements','',result['scope'],'',f"Three repetitions per case; observed medians. No Android, YouTube or Snaptube speed claim.",'','| Case | 0.6.1 baseline total (s) | Optimized total (s) | Total change | Output bytes before / after |','|---|---:|---:|---:|---:|']
for r in rows:lines.append(f"| {r['case']} | {r['baseline_median_total_seconds']:.3f} | {r['optimized_median_total_seconds']:.3f} | {r['observed_total_change_percent']:+.1f}% | {int(r['output_bytes_baseline'])} / {int(r['output_bytes_optimized'])} |")
lines+=['',f"Controlled HLS N=1 → N=4 total-time change: {(n4/n1-1)*100:+.1f}%.",'','Negative time changes are faster in this fixture; positive changes are slower. These are observations, not promised performance.','',*['- '+note for note in result['notes']],'','Raw JSON includes transfer start, average/peak observed speeds, phase timing, CPU, approximate RSS, output bytes and successful full decoding.']
(HERE/'build/performance-report.md').write_text('\n'.join(lines)+'\n')
print('\n'.join(lines))
