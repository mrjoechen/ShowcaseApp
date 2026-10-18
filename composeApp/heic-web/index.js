// A HEIC worker per decode keeps the UI responsive and releases the WASM heap on completion.
const jobs = new Map();
// Browser AVIF decoding keeps no WASM heap, so one long-lived worker serves every image.
const avifJobs = new Map();
let avifWorker = null;

export function cancel(id) {
  const job = jobs.get(id);
  if (job) {
    clearTimeout(job.timer);
    job.worker.terminate();
    jobs.delete(id);
  }
  const avifJob = avifJobs.get(id);
  if (avifJob) {
    // The shared worker finishes this image; its result is ignored.
    clearTimeout(avifJob.timer);
    avifJobs.delete(id);
  }
}

export function decode(id, encoded, success, failure) {
  try {
    const worker = new Worker(new URL('./worker.js', import.meta.url), { type: 'module' });
    const fail = message => { cancel(id); failure(message); };
    const timer = setTimeout(() => fail('HEIC decoding timed out'), 60000);
    jobs.set(id, { worker, timer });
    worker.onerror = event => fail(event.message || 'HEIC worker failed');
    worker.onmessageerror = () => fail('Invalid HEIC worker response');
    worker.onmessage = ({ data }) => {
      cancel(id);
      if (data.error) failure(data.error);
      else success(data.width, data.height, data.pixels);
    };
    worker.postMessage(encoded);
  } catch (error) {
    cancel(id);
    failure(String(error));
  }
}

function sharedAvifWorker() {
  if (avifWorker) return avifWorker;
  const worker = new Worker(new URL('./avif-worker.js', import.meta.url), { type: 'module' });
  const failAll = message => {
    if (avifWorker === worker) avifWorker = null;
    worker.terminate();
    const pending = [...avifJobs.values()];
    avifJobs.clear();
    for (const job of pending) {
      clearTimeout(job.timer);
      job.failure(message);
    }
  };
  worker.onerror = event => failAll(event.message || 'AVIF worker failed');
  worker.onmessageerror = () => failAll('Invalid AVIF worker response');
  worker.onmessage = ({ data }) => {
    const job = avifJobs.get(data.id);
    if (!job) return;
    cancel(data.id);
    if (data.error) job.failure(data.error);
    else job.success(data.width, data.height, data.pixels);
  };
  avifWorker = worker;
  return worker;
}

export function decodeAvif(id, encoded, success, failure) {
  try {
    const worker = sharedAvifWorker();
    const timer = setTimeout(() => { cancel(id); failure('AVIF decoding timed out'); }, 60000);
    avifJobs.set(id, { success, failure, timer });
    worker.postMessage({ id, encoded });
  } catch (error) {
    cancel(id);
    failure(String(error));
  }
}

// Kotlin/Wasm imports external objects as a default export; Kotlin/JS uses the namespace.
export default { decode, decodeAvif, cancel };
