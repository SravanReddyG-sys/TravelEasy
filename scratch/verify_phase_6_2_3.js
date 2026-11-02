const http = require('http');

function fetchJson(url) {
  return new Promise((resolve, reject) => {
    http.get(url, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, data: JSON.parse(data) });
        } catch (e) {
          resolve({ status: res.statusCode, raw: data });
        }
      });
    }).on('error', reject);
  });
}

function resolveBusImageUrl(img, isDev = true) {
  if (!img) return 'assets/images/travel-easy-bus.png';
  if (img.startsWith('http://') || img.startsWith('https://') || img.startsWith('assets/')) {
    return img;
  }
  const cleanUrl = img.startsWith('/') ? img : `/${img}`;
  const base = isDev ? 'http://localhost:8080' : '';
  return `${base}${cleanUrl}`;
}

function simulateParseBooking(b, isDev = true) {
  const isBus = b.serviceType === 'BUS';
  let displayTitle = b.serviceTitle || (isBus ? 'Bus Booking' : 'Hotel Booking');
  let displaySubtitle = '';

  if (isBus) {
    const match = displayTitle.match(/^(.*?)\s*\((.*?)\s+to\s+(.*?)\)$/i);
    let operator = b.operatorBusinessName || '';
    let origin = '';
    let dest = '';
    if (match) {
      if (!operator) operator = match[1].trim();
      origin = match[2].trim();
      dest = match[3].trim();
      displayTitle = `${origin} → ${dest}`;
    } else if (displayTitle.toLowerCase().includes(' to ')) {
      displayTitle = displayTitle.replace(/\s+to\s+/i, ' → ');
    }
    if (!operator) operator = 'TravelEasy Partner';

    // Correction B: Dedicated AC / Non-AC classification from registered Bus
    let acClassification = 'AC';
    const facility = (b.facilityType || b.bus?.facilityType || '').toUpperCase();
    if (facility.includes('NON')) {
      acClassification = 'Non-AC';
    } else if (facility === 'AC') {
      acClassification = 'AC';
    } else if (b.hasAc !== undefined) {
      acClassification = b.hasAc ? 'AC' : 'Non-AC';
    }

    // Correction C: Registered Bus Type (Seater, Sleeper, Seater Cum Sleeper)
    let busType = b.busType || b.bus?.busType || 'Sleeper';
    const upperType = busType.toUpperCase();
    if (upperType.includes('SEATER CUM SLEEPER') || upperType.includes('SEATER / SLEEPER') || upperType.includes('HYBRID')) {
      busType = 'Seater Cum Sleeper';
    } else if (upperType.includes('SEATER')) {
      busType = 'Seater';
    } else if (upperType.includes('SLEEPER')) {
      busType = 'Sleeper';
    }

    displaySubtitle = `${operator} • ${acClassification} ${busType}`;
  }

  const extImg = b.exteriorImageUrl || b.bus?.exteriorImageUrl || '';
  const thumb = isBus ? resolveBusImageUrl(extImg, isDev) : 'assets/images/hotel.png';

  return {
    displayTitle,
    displaySubtitle,
    thumbnail: thumb
  };
}

async function verify() {
  console.log('====================================================');
  console.log('   PHASE 6.2.3 VERIFICATION: MY BOOKINGS BUS INFO  ');
  console.log('====================================================\n');

  let passed = 0;
  let failed = 0;

  function assert(condition, testName, details = '') {
    if (condition) {
      console.log(`[PASS] ${testName}`);
      if (details) console.log(`       -> ${details}`);
      passed++;
    } else {
      console.error(`[FAIL] ${testName}`);
      if (details) console.error(`       -> ${details}`);
      failed++;
    }
  }

  // TEST GROUP 1: Backend DTO Enrichment via API Gateway
  console.log('--- Test Group 1: Backend Booking API Enrichment ---');
  try {
    const res = await fetchJson('http://localhost:8080/api/bookings/admin/all');
    assert(res.status === 200, 'All Bookings HTTP Status 200', `Status: ${res.status}`);
    const bookings = res.data || [];
    assert(bookings.length > 0, 'Returned non-empty bookings list', `Count: ${bookings.length}`);

    const b109 = bookings.find(b => b.id === 109);
    assert(!!b109, 'Found Booking 109 in response', `Booking ID: 109`);
    if (b109) {
      assert(b109.exteriorImageUrl === '/api/buses/images/exterior_b803274122694ebe8c877fd616eb8541_1793554803049.jpg',
        'Correction A: exteriorImageUrl matches registered bus exterior image',
        `exteriorImageUrl: "${b109.exteriorImageUrl}"`);
      assert(b109.facilityType === 'AC',
        'Correction B: facilityType is "AC"',
        `facilityType: "${b109.facilityType}"`);
      assert(b109.busType === 'Seater Cum Sleeper',
        'Correction C: busType is "Seater Cum Sleeper"',
        `busType: "${b109.busType}"`);
      assert(b109.operatorBusinessName === 'GSR Travels',
        'Legal Business Name: operatorBusinessName is "GSR Travels"',
        `operatorBusinessName: "${b109.operatorBusinessName}"`);
    }

    const b108 = bookings.find(b => b.id === 108);
    if (b108) {
      assert(b108.exteriorImageUrl === '/api/buses/images/exterior_b803274122694ebe8c877fd616eb8541_1793554803049.jpg',
        'Historical Booking 108: exteriorImageUrl enriched correctly',
        `exteriorImageUrl: "${b108.exteriorImageUrl}"`);
      assert(b108.operatorBusinessName === 'GSR Travels',
        'Historical Booking 108: resolves business name over legacy personal name',
        `operatorBusinessName: "${b108.operatorBusinessName}"`);
    }
  } catch (err) {
    assert(false, 'Backend API call succeeded', err.message);
  }

  // TEST GROUP 2: Frontend Parsing & Display Subtitle/Thumbnail
  console.log('\n--- Test Group 2: Frontend Parsing & Subtitle Formatting ---');
  try {
    const rawRes = await fetchJson('http://localhost:8080/api/bookings/admin/all');
    const bookings = rawRes.data || [];
    const b109 = bookings.find(b => b.id === 109);
    if (b109) {
      const parsed109 = simulateParseBooking(b109, true);
      assert(parsed109.displayTitle === 'Kadapa → Vijayawada',
        'Route arrow formatted correctly',
        `displayTitle: "${parsed109.displayTitle}"`);
      assert(parsed109.displaySubtitle === 'GSR Travels • AC Seater Cum Sleeper',
        'displaySubtitle reflects: <Operator> • <AC Status> <Bus Type>',
        `displaySubtitle: "${parsed109.displaySubtitle}"`);
      assert(parsed109.thumbnail === 'http://localhost:8080/api/buses/images/exterior_b803274122694ebe8c877fd616eb8541_1793554803049.jpg',
        'thumbnail resolves to Gateway URL in dev mode',
        `thumbnail: "${parsed109.thumbnail}"`);
    }

    const b108 = bookings.find(b => b.id === 108);
    if (b108) {
      const parsed108 = simulateParseBooking(b108, true);
      assert(parsed108.displaySubtitle === 'GSR Travels • AC Seater Cum Sleeper',
        'Historical Booking 108 subtitle uses legal business name "GSR Travels"',
        `displaySubtitle: "${parsed108.displaySubtitle}"`);
    }

    // Edge Cases: Non-AC Seater bus
    const nonAcMock = {
      serviceType: 'BUS',
      serviceTitle: 'Super Travels (Hyderabad to Bengaluru)',
      operatorBusinessName: 'Super Travels',
      facilityType: 'NON_AC',
      busType: 'Seater',
      exteriorImageUrl: null
    };
    const parsedNonAc = simulateParseBooking(nonAcMock, true);
    assert(parsedNonAc.displaySubtitle === 'Super Travels • Non-AC Seater',
      'Non-AC Seater subtitle: "Super Travels • Non-AC Seater"',
      `displaySubtitle: "${parsedNonAc.displaySubtitle}"`);
    assert(parsedNonAc.thumbnail === 'assets/images/travel-easy-bus.png',
      'Missing exterior image falls back to assets/images/travel-easy-bus.png',
      `thumbnail: "${parsedNonAc.thumbnail}"`);

    // Edge Cases: AC Sleeper bus
    const sleeperMock = {
      serviceType: 'BUS',
      serviceTitle: 'Orange Travels (Bengaluru to Goa)',
      operatorBusinessName: 'Orange Travels',
      facilityType: 'AC',
      busType: 'Sleeper',
      exteriorImageUrl: 'https://cdn.example.com/bus.jpg'
    };
    const parsedSleeper = simulateParseBooking(sleeperMock, true);
    assert(parsedSleeper.displaySubtitle === 'Orange Travels • AC Sleeper',
      'AC Sleeper subtitle: "Orange Travels • AC Sleeper"',
      `displaySubtitle: "${parsedSleeper.displaySubtitle}"`);
    assert(parsedSleeper.thumbnail === 'https://cdn.example.com/bus.jpg',
      'Direct HTTP image preserved without modification',
      `thumbnail: "${parsedSleeper.thumbnail}"`);
  } catch (err) {
    assert(false, 'Frontend simulation succeeded', err.message);
  }

  console.log('\n====================================================');
  console.log(`SUMMARY: ${passed} PASSED, ${failed} FAILED`);
  console.log('====================================================');

  process.exit(failed === 0 ? 0 : 1);
}

verify();
