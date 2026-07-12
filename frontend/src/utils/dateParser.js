/**
 * Parses a robust date from various formats, supporting separated and compact dates.
 * 
 * @param {any} val - The raw date value to parse.
 * @param {string} compactFormat - The compact format choice: 'YYYYMMDD' or 'YYYYDDMM'.
 * @returns {Date}
 */
export function parseRobustDate(val, compactFormat = 'YYYYMMDD') {
  if (val === null || val === undefined || val === '') {
    return new Date();
  }

  if (val instanceof Date) {
    return isNaN(val.getTime()) ? new Date() : val;
  }

  if (typeof val === 'number') {
    if (val > 0) {
      if (val > 1000000000) {
        return new Date(val < 10000000000 ? val * 1000 : val);
      }
      const excelEpoch = new Date(1899, 11, 30);
      const d = new Date(excelEpoch.getTime() + val * 86400 * 1000);
      return isNaN(d.getTime()) ? new Date() : d;
    }
    return new Date();
  }

  let str = val.toString().trim();
  if (!str) return new Date();

  const parsedMs = Date.parse(str);
  if (!isNaN(parsedMs)) {
    return new Date(parsedMs);
  }

  const parts = str.split(/[\sT]+/);
  const datePart = parts[0];
  let timePart = parts[1] || '';

  let year = null;
  let month = null;
  let day = null;

  const ymdMatch = datePart.match(/^(\d{4})[./-](\d{1,2})[./-](\d{1,2})$/);
  if (ymdMatch) {
    year = parseInt(ymdMatch[1], 10);
    month = parseInt(ymdMatch[2], 10) - 1;
    day = parseInt(ymdMatch[3], 10);
  }

  if (year === null) {
    const compactYmdMatch = datePart.match(/^(\d{4})(\d{2})(\d{2})$/);
    if (compactYmdMatch) {
      const yVal = parseInt(compactYmdMatch[1], 10);
      const aVal = parseInt(compactYmdMatch[2], 10);
      const bVal = parseInt(compactYmdMatch[3], 10);

      year = yVal;
      if (compactFormat === 'YYYYDDMM') {
        day = aVal;
        month = bVal - 1;
      } else {
        month = aVal - 1;
        day = bVal;
      }
    }
  }

  if (year === null) {
    const dmyMatch = datePart.match(/^(\d{1,2})[./-](\d{1,2})[./-](\d{4})$/);
    if (dmyMatch) {
      const a = parseInt(dmyMatch[1], 10);
      const b = parseInt(dmyMatch[2], 10);
      year = parseInt(dmyMatch[3], 10);
      if (a > 12) {
        day = a;
        month = b - 1;
      } else if (b > 12) {
        day = b;
        month = a - 1;
      } else {
        day = a;
        month = b - 1;
      }
    }
  }

  if (year === null) {
    const dmyShortMatch = datePart.match(/^(\d{1,2})[./-](\d{1,2})[./-](\d{2})$/);
    if (dmyShortMatch) {
      const a = parseInt(dmyShortMatch[1], 10);
      const b = parseInt(dmyShortMatch[2], 10);
      const shortYear = parseInt(dmyShortMatch[3], 10);
      year = shortYear + (shortYear < 50 ? 2000 : 1900);
      if (a > 12) {
        day = a;
        month = b - 1;
      } else if (b > 12) {
        day = b;
        month = a - 1;
      } else {
        day = a;
        month = b - 1;
      }
    }
  }

  if (year !== null && month !== null && day !== null) {
    let hours = 0;
    let minutes = 0;
    let seconds = 0;

    if (timePart) {
      const timeMatch = timePart.match(/^(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?/);
      if (timeMatch) {
        hours = parseInt(timeMatch[1], 10);
        minutes = parseInt(timeMatch[2], 10);
        seconds = timeMatch[3] ? parseInt(timeMatch[3], 10) : 0;
      }
    }

    const d = new Date(year, month, day, hours, minutes, seconds);
    if (!isNaN(d.getTime())) {
      return d;
    }
  }

  return new Date();
}

/**
 * Scans rows of imported extract to detect whether compact date strings (YYYYMMDD)
 * are configured in YYYYMMDD vs YYYYDDMM format.
 * 
 * @param {any[][]} rows - The parsed raw rows from the file.
 * @param {number} dateColumnIndex - The index of the date column.
 * @returns {string} - 'YYYYMMDD' or 'YYYYDDMM'
 */
export function detectCompactDateFormat(rows, dateColumnIndex) {
  let detectedCompactFormat = 'YYYYMMDD'; // default

  for (const row of rows) {
    const rawDateStr = row[dateColumnIndex];
    if (rawDateStr !== null && rawDateStr !== undefined && rawDateStr !== '') {
      const str = rawDateStr.toString().trim();
      const parts = str.split(/[\sT]+/);
      const datePart = parts[0];
      const compactYmdMatch = datePart.match(/^(\d{4})(\d{2})(\d{2})$/);
      if (compactYmdMatch) {
        const aVal = parseInt(compactYmdMatch[2], 10);
        const bVal = parseInt(compactYmdMatch[3], 10);
        if (aVal > 12) {
          detectedCompactFormat = 'YYYYDDMM';
          break; // Found unambiguous format, stop scanning
        } else if (bVal > 12) {
          detectedCompactFormat = 'YYYYMMDD';
          break; // Found unambiguous format, stop scanning
        }
      }
    }
  }

  return detectedCompactFormat;
}
