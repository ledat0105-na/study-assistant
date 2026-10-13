const JobPoller = (function() {
  const TERMINAL_STATES = ['SUCCEEDED', 'FAILED', 'CANCELLED'];
  const DEFAULT_INTERVAL = 2000;
  const DEFAULT_TIMEOUT = 120000;

  /**
   * Polls a job until a terminal state is reached.
   * @param {Function} fetchJob - A function that returns a Promise resolving to a JobDTO.
   * @param {Object} options - { interval, timeout, signal, onProgress }
   * @returns {Promise<JobDTO>}
   */
  async function pollJob(fetchJob, options = {}) {
    const interval = options.interval || DEFAULT_INTERVAL;
    const timeout = options.timeout || DEFAULT_TIMEOUT;
    const startTime = Date.now();

    return new Promise((resolve, reject) => {
      async function attempt() {
        if (options.signal && options.signal.aborted) {
          return reject(new DOMException('Polling aborted', 'AbortError'));
        }

        if (Date.now() - startTime > timeout) {
          return reject({ status: 504, code: 'TIMEOUT', message: 'Quá thời gian chờ phản hồi từ máy chủ.' });
        }

        try {
          const job = await fetchJob();
          
          if (options.onProgress) {
            options.onProgress(job);
          }

          if (TERMINAL_STATES.includes(job.status)) {
            if (job.status === 'FAILED') {
              return reject({ status: 500, code: job.errorCode, message: job.errorSummary || 'Xử lý thất bại.' });
            } else if (job.status === 'CANCELLED') {
              return reject({ status: 400, code: 'CANCELLED', message: 'Tác vụ đã bị huỷ.' });
            }
            return resolve(job);
          }

          setTimeout(attempt, interval);
        } catch (error) {
          // If network error, we might want to retry, but for simplicity we fail fast on 4xx/5xx
          reject(error);
        }
      }

      attempt();
    });
  }

  return { pollJob };
})();
