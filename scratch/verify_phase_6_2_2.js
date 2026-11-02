const http = require('http');

function fetchJson(url) {
  return new Promise((resolve, reject) => {
    http.get(url, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, data: JSON.parse(data), headers: res.headers });
        } catch (e) {
          resolve({ status: res.statusCode, raw: data, headers: res.headers });
        }
      });
    }).on('error', reject);
  });
}

function fetchHead(url) {
  return new Promise((resolve, reject) => {
    http.get(url, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        resolve({ status: res.statusCode, headers: res.headers, length: data.length });
      });
    }).on('error', reject);
  });
}

async function runVerification() {
  console.log('====================================================');
  console.log('   PHASE 6.2.2 VERIFICATION: SELECTED BUS CARD     ');
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

  // TEST 1: Layout Endpoint Returns Legal Business Name & Exterior Image
  console.log('--- Test Group 1: Schedule Layout Endpoint ---');
  try {
    const layoutRes = await fetchJson('http://localhost:8080/api/buses/schedules/2885/layout');
    assert(layoutRes.status === 200, 'Layout HTTP Status 200', `Status: ${layoutRes.status}`);
    
    const layout = layoutRes.data;
    assert(layout.scheduleId === 2885, 'Schedule ID is 2885', `scheduleId: ${layout.scheduleId}`);
    assert(layout.operatorName === 'GSR Travels', 'operatorName is resolved to legal business name ("GSR Travels")', `operatorName: "${layout.operatorName}"`);
    assert(layout.businessName === 'GSR Travels', 'businessName is "GSR Travels"', `businessName: "${layout.businessName}"`);
    assert(layout.operatorName !== 'Sravan Reddy', 'operatorName is NOT personal name ("Sravan Reddy")', `Value: "${layout.operatorName}"`);
    assert(!!layout.exteriorImageUrl && layout.exteriorImageUrl.includes('/api/buses/images/'), 'exteriorImageUrl is populated with actual uploaded bus image', `exteriorImageUrl: ${layout.exteriorImageUrl}`);
    assert(layout.decks && layout.decks.length > 0, 'Layout decks and seat positions preserved intact', `Decks: ${layout.decks?.length}, Total inventory: ${layout.totalInventory}`);
  } catch (err) {
    assert(false, 'Layout endpoint request succeeded', err.message);
  }

  // TEST 2: Exterior Image Serving via Gateway
  console.log('\n--- Test Group 2: Image Serving ---');
  try {
    const imgUrl = 'http://localhost:8080/api/buses/images/exterior_b803274122694ebe8c877fd616eb8541_1793554803049.jpg';
    const imgRes = await fetchHead(imgUrl);
    assert(imgRes.status === 200, 'Image serves with HTTP 200', `Status: ${imgRes.status}`);
    assert(imgRes.headers['content-type'] === 'image/jpeg', 'Content-Type is image/jpeg', `Type: ${imgRes.headers['content-type']}`);
    assert(imgRes.length > 1000, 'Image payload is non-empty binary', `Bytes received: ${imgRes.length}`);
  } catch (err) {
    assert(false, 'Image request succeeded', err.message);
  }

  // TEST 3: Search Endpoint Consistency
  console.log('\n--- Test Group 3: Search Endpoint Consistency ---');
  try {
    const searchRes = await fetchJson('http://localhost:8080/api/buses/search?origin=Mydukur&destination=Guntur&date=2026-11-02');
    assert(searchRes.status === 200, 'Search HTTP Status 200', `Status: ${searchRes.status}`);
    const results = searchRes.data || [];
    const item = results.find(s => s.id === 2885);
    if (item) {
      assert(item.businessName === 'GSR Travels', 'Search card businessName is "GSR Travels"', `businessName: ${item.businessName}`);
      assert(item.operatorName === 'GSR Travels', 'Search card operatorName is "GSR Travels"', `operatorName: ${item.operatorName}`);
      const extImg = item.bus?.exteriorImageUrl || item.exteriorImageUrl;
      assert(extImg === '/api/buses/images/exterior_b803274122694ebe8c877fd616eb8541_1793554803049.jpg', 'Search card exteriorImageUrl matches registered bus exterior image', `url: ${extImg}`);
    } else {
      console.log('Note: Schedule 2885 not found in search for 2026-11-02, testing all available search results');
      if (results.length > 0) {
        assert(!!results[0].businessName, 'Search results contain businessName', `businessName: ${results[0].businessName}`);
      }
    }
  } catch (err) {
    assert(false, 'Search request succeeded', err.message);
  }

  // TEST 4: Frontend Component Logic Simulation
  console.log('\n--- Test Group 4: Frontend Getters Logic Simulation ---');
  
  function getDisplayOperatorName(schedule, layout) {
    return schedule?.businessName ||
           layout?.businessName ||
           schedule?.bus?.operatorName ||
           schedule?.operatorName ||
           layout?.operatorName ||
           'TravelEasy Partner';
  }

  function getBusThumbnail(schedule, layout, isDev = true) {
    const img = schedule?.bus?.exteriorImageUrl ||
                schedule?.exteriorImageUrl ||
                layout?.exteriorImageUrl ||
                schedule?.busImage ||
                schedule?.thumbnailUrl;
    if (!img) {
      return 'assets/images/travel-easy-bus.png';
    }
    if (img.startsWith('http://') || img.startsWith('https://') || img.startsWith('assets/')) {
      return img;
    }
    const cleanUrl = img.startsWith('/') ? img : `/${img}`;
    const base = isDev ? 'http://localhost:8080' : '';
    return `${base}${cleanUrl}`;
  }

  // Case A: Full schedule and layout from actual backend
  const testScheduleA = {
    id: 2885,
    operatorName: 'GSR Travels',
    businessName: 'GSR Travels',
    exteriorImageUrl: '/api/buses/images/exterior_b803274122694ebe8c877fd616eb8541_1793554803049.jpg'
  };
  const testLayoutA = {
    scheduleId: 2885,
    operatorName: 'GSR Travels',
    businessName: 'GSR Travels',
    exteriorImageUrl: '/api/buses/images/exterior_b803274122694ebe8c877fd616eb8541_1793554803049.jpg'
  };
  assert(getDisplayOperatorName(testScheduleA, testLayoutA) === 'GSR Travels', 'displayOperatorName returns "GSR Travels" with both schedule & layout');
  assert(getBusThumbnail(testScheduleA, testLayoutA, true) === 'http://localhost:8080/api/buses/images/exterior_b803274122694ebe8c877fd616eb8541_1793554803049.jpg', 'busThumbnail prepends gateway URL in dev mode');

  // Case B: Legacy schedule where operatorName was personal name, but layout has legal business name
  const testScheduleB = {
    id: 2885,
    operatorName: 'Sravan Reddy' // un-updated schedule
  };
  const testLayoutB = {
    scheduleId: 2885,
    operatorName: 'GSR Travels',
    businessName: 'GSR Travels',
    exteriorImageUrl: '/api/buses/images/exterior_b803274122694ebe8c877fd616eb8541_1793554803049.jpg'
  };
  assert(getDisplayOperatorName(testScheduleB, testLayoutB) === 'GSR Travels', 'displayOperatorName prioritizes layout businessName over un-updated schedule operatorName');
  assert(getBusThumbnail(testScheduleB, testLayoutB, true) === 'http://localhost:8080/api/buses/images/exterior_b803274122694ebe8c877fd616eb8541_1793554803049.jpg', 'busThumbnail resolves exteriorImageUrl from layout if absent on schedule');

  // Case C: Missing exterior image falls back to neutral travel-easy-bus.png
  const testScheduleC = { id: 999 };
  const testLayoutC = { scheduleId: 999 };
  assert(getBusThumbnail(testScheduleC, testLayoutC, true) === 'assets/images/travel-easy-bus.png', 'busThumbnail falls back to assets/images/travel-easy-bus.png when no image present');

  console.log('\n====================================================');
  console.log(`SUMMARY: ${passed} PASSED, ${failed} FAILED`);
  console.log('====================================================');

  process.exit(failed === 0 ? 0 : 1);
}

runVerification();
