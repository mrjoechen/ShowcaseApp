// A worker per decode keeps the UI responsive and releases the WASM heap on completion.
const jobs = new Map();

export function cancel(id) {
  const job = jobs.get(id);
  if (!job) return;
  clearTimeout(job.timer);
  job.worker.terminate();
  jobs.delete(id);
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

// Kotlin/Wasm imports external objects as a default export; Kotlin/JS uses the namespace.
export default { decode, cancel };
