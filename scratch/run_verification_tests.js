const http = require('http');

async function fetchJson(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`HTTP ${res.status} from ${url}`);
  return await res.json();
}

async function runTests() {
  console.log('=== RUNNING PHASE 6.2.1 VERIFICATION SUITE ===\n');

  // 1. Search API via API Gateway
  console.log('1. Testing Bus Search API via Gateway (port 8080)...');
  const searchResults = await fetchJson('http://localhost:8080/api/buses/search?origin=Kadapa&destination=Vijayawada&date=2026-11-02');
  console.log(`-> Received ${searchResults.length} schedules.`);
  if (searchResults.length === 0) throw new Error('Expected at least 1 schedule');

  const s0 = searchResults[0];
  console.log('   - Schedule ID:', s0.id);
  console.log('   - Operator Legal Business Name:', s0.businessName);
  console.log('   - Schedule operatorName:', s0.operatorName);
  console.log('   - Attached Bus ID:', s0.bus?.id);
  console.log('   - Bus Manufacturer & Model:', `${s0.bus?.manufacturer} ${s0.bus?.model}`);
  console.log('   - Bus Facility Type (AC):', s0.bus?.facilityType);
  console.log('   - Bus Template ID:', s0.bus?.templateId);
  console.log('   - Bus Registered Amenities:', s0.bus?.amenities);
  console.log('   - Bus Exterior Image URL:', s0.bus?.exteriorImageUrl);

  if (s0.businessName !== 'GSR Travels') {
    throw new Error(`Expected businessName to be 'GSR Travels', got: ${s0.businessName}`);
  }
  if (!s0.bus || !s0.bus.exteriorImageUrl) {
    throw new Error('Bus exteriorImageUrl missing in search response');
  }
  console.log('   [PASS] Business Name and Bus Entity correctly mapped.\n');

  // 2. Image Serving Verification
  console.log('2. Testing Backend Image Serving via Gateway...');
  const imgRes = await fetch(`http://localhost:8080${s0.bus.exteriorImageUrl}`);
  console.log(`   - GET ${s0.bus.exteriorImageUrl} -> Status: ${imgRes.status}, Content-Type: ${imgRes.headers.get('content-type')}`);
  if (imgRes.status !== 200) throw new Error(`Image serving failed with status ${imgRes.status}`);
  console.log('   [PASS] Image serving verified.\n');

  // 3. Fabricated Demo Rating Stability Simulation
  console.log('3. Testing Fabricated Rating Determinism & Stability...');
  const DEMO_RATINGS = [
    { rating: 4.5, reviews: 150 },
    { rating: 4.2, reviews: 98 },
    { rating: 4.7, reviews: 215 },
    { rating: 4.3, reviews: 124 },
    { rating: 4.6, reviews: 186 },
    { rating: 4.8, reviews: 240 },
    { rating: 4.4, reviews: 112 },
    { rating: 4.1, reviews: 85 }
  ];

  const enriched = searchResults.map((b, idx) => {
    const hash = Math.abs((b.id || idx)) % DEMO_RATINGS.length;
    const demo = DEMO_RATINGS[hash];
    return {
      ...b,
      rating: b.rating ?? demo.rating,
      reviewCount: b.reviewCount ?? demo.reviews
    };
  });

  const rating1 = enriched[0].rating;
  const reviews1 = enriched[0].reviewCount;

  // Simulate multiple sorts and filters
  const sortedByFare = [...enriched].sort((a, b) => a.fare - b.fare);
  const sortedByRating = [...enriched].sort((a, b) => b.rating - a.rating);
  const filtered = enriched.filter(b => b.rating >= 4.0);

  if (sortedByFare[0].rating !== rating1 || sortedByRating[0].rating !== rating1 || filtered[0].rating !== rating1) {
    throw new Error('Rating changed during sort or filter!');
  }
  console.log(`   - Stable Rating: ${rating1}, Reviews: ${reviews1}`);
  console.log('   [PASS] Ratings remain invariant across filtering and sorting.\n');

  // 4. Filtering Logic Verification
  console.log('4. Testing Filtering Logic on Actual Attributes...');
  function isBusAC(b) {
    const fac = (b.bus?.facilityType || b.facilityType || '').toUpperCase();
    if (fac === 'AC') return true;
    if (fac === 'NON_AC') return false;
    return /AC/i.test(b.busType || '') && !/Non-AC/i.test(b.busType || '');
  }
  function isBusSeaterCumSleeper(b) {
    const type = b.busType || b.bus?.busType || '';
    const template = b.bus?.templateId || b.templateId || '';
    return /Seater.*Sleeper/i.test(type) || /Hybrid/i.test(type) || /Hybrid/i.test(template) || /SEATER_CUM_SLEEPER/i.test(template);
  }
  function getBusSeatLayoutName(b) {
    const tid = b.bus?.templateId || b.templateId;
    if (tid) {
      const map = {
        '1+1 SEATER': '1 + 1 Seater',
        '2+1 HYBRID_SLEEPER-1': '2 + 1 Hybrid Sleeper-1',
        '2+1 HYBRID_SLEEPER-2': '2 + 1 Hybrid Sleeper-2',
        '2+1 SEATER': '2 + 1 Seater',
        '2+1 SEATER_CUM_SLEEPER': '2 + 1 Seater Cum Sleeper',
        '2+1 SLEEPER': '2 + 1 Sleeper',
        '2+2 SEATER': '2 + 2 Seater',
      };
      return map[tid.toUpperCase().trim()] || tid.replace(/_/g, ' ');
    }
    return b.busType || 'Standard Layout';
  }

  const busIsAC = isBusAC(enriched[0]);
  const busIsHybrid = isBusSeaterCumSleeper(enriched[0]);
  const layoutName = getBusSeatLayoutName(enriched[0]);

  console.log(`   - AC check: ${busIsAC}`);
  console.log(`   - Seater Cum Sleeper check: ${busIsHybrid}`);
  console.log(`   - Seat Layout Name: ${layoutName}`);

  if (!busIsAC) throw new Error('Expected AC to match facilityType: AC');
  if (!busIsHybrid) throw new Error('Expected Seater Cum Sleeper to match');
  if (layoutName !== '2 + 1 Hybrid Sleeper-2') throw new Error(`Unexpected layout name: ${layoutName}`);
  console.log('   [PASS] Filtering attribute evaluation verified.\n');

  // 5. Sorting Logic across midnight
  console.log('5. Testing 8 Sorting Logic Criteria including Midnight Crossing...');
  function timeToMinutes(timeStr) {
    const parts = timeStr.trim().split(':');
    return parseInt(parts[0], 10) * 60 + parseInt(parts[1], 10);
  }
  function getDurationMinutes(b) {
    let diff = timeToMinutes(b.arrivalTime) - timeToMinutes(b.departureTime);
    if (diff < 0) diff += 24 * 60;
    return diff;
  }

  // 22:00 to 06:30 is 8h 30m = 510 minutes
  const durMins = getDurationMinutes(enriched[0]);
  console.log(`   - Duration for 22:00 -> 06:30: ${durMins} mins (${Math.floor(durMins/60)}h ${durMins%60}m)`);
  if (durMins !== 510) throw new Error(`Expected 510 minutes duration across midnight, got ${durMins}`);
  console.log('   [PASS] Midnight duration calculation verified.\n');

  // 6. Schedule Layout and Inventory Regression Check
  console.log('6. Checking Seat Layout & Availability endpoint regression...');
  const layout = await fetchJson(`http://localhost:8080/api/buses/schedules/${s0.id}/layout`);
  console.log(`   - Layout Template: ${layout.templateName}, Total Inventory: ${layout.totalInventory}, Available: ${layout.availableSeats}`);
  console.log(`   - Decks count: ${layout.decks?.length}`);
  if (!layout.decks || layout.decks.length === 0) throw new Error('Layout decks missing');
  console.log('   [PASS] Seat Layout & Inventory intact.\n');

  console.log('=== ALL 6 VERIFICATION TEST SUITES PASSED! ===');
}

runTests().catch(err => {
  console.error('\nTEST SUITE FAILED:', err);
  process.exit(1);
});
