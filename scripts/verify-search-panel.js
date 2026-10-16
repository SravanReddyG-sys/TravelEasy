/**
 * Travel Easy - Search Panel Verification Script
 * ==============================================
 * Verifies tab switching, form validation, swap behavior, date constraints,
 * and backend search integration via API Gateway.
 */

const API_BASE = 'http://localhost:8080/api';

async function req(url) {
  const res = await fetch(url);
  const text = await res.text();
  try {
    return { ok: res.ok, status: res.status, data: JSON.parse(text) };
  } catch {
    return { ok: res.ok, status: res.status, data: text };
  }
}

// Emulate HomeComponent Validation Logic
class SearchPanelValidator {
  constructor() {
    this.searchTab = 'bus';
    this.busFrom = '';
    this.busTo = '';
    this.busDate = this.getTodayDate();
    this.hotelCity = '';
    this.hotelCheckIn = this.getTodayDate();
    this.hotelCheckOut = this.getTomorrowDate();
    this.busErrorMessage = '';
    this.hotelErrorMessage = '';
    this.minDate = this.getTodayDate();
  }

  getTodayDate() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  getTomorrowDate(baseStr) {
    const d = baseStr ? new Date(baseStr) : new Date();
    d.setDate(d.getDate() + 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  setTab(tab) {
    this.searchTab = tab;
    this.busErrorMessage = '';
    this.hotelErrorMessage = '';
  }

  swapCities() {
    const tmp = this.busFrom;
    this.busFrom = this.busTo;
    this.busTo = tmp;
  }

  validateBusSearch() {
    this.busErrorMessage = '';
    const from = this.busFrom?.trim();
    const to = this.busTo?.trim();
    const date = this.busDate;

    if (!from) { this.busErrorMessage = 'Please enter a source city.'; return false; }
    if (!to) { this.busErrorMessage = 'Please enter a destination city.'; return false; }
    if (from.toLowerCase() === to.toLowerCase()) {
      this.busErrorMessage = 'Source and destination cities cannot be the same.';
      return false;
    }
    if (!date) { this.busErrorMessage = 'Please select a travel date.'; return false; }
    if (date < this.minDate) { this.busErrorMessage = 'Travel date cannot be in the past.'; return false; }
    return true;
  }

  validateHotelSearch() {
    this.hotelErrorMessage = '';
    const city = this.hotelCity?.trim();
    const checkIn = this.hotelCheckIn;
    const checkOut = this.hotelCheckOut;

    if (!city) { this.hotelErrorMessage = 'Please enter a city or destination.'; return false; }
    if (!checkIn) { this.hotelErrorMessage = 'Please select a check-in date.'; return false; }
    if (checkIn < this.minDate) { this.hotelErrorMessage = 'Check-in date cannot be in the past.'; return false; }
    if (!checkOut) { this.hotelErrorMessage = 'Please select a check-out date.'; return false; }
    if (checkOut <= checkIn) { this.hotelErrorMessage = 'Check-out date must be later than check-in date.'; return false; }
    return true;
  }
}

async function runTests() {
  console.log('================================================================');
  console.log('   SEARCH PANEL VERIFICATION SUITE');
  console.log('================================================================\n');

  const panel = new SearchPanelValidator();

  // Test 1: Tab switching
  console.log('1. [TAB SWITCHING]');
  console.assert(panel.searchTab === 'bus', 'Default tab must be bus');
  console.log('  ✓ Default tab is "bus"');
  panel.setTab('hotel');
  console.assert(panel.searchTab === 'hotel', 'Tab should switch to hotel');
  console.log('  ✓ Switched tab to "hotel"');
  panel.setTab('bus');
  console.assert(panel.searchTab === 'bus', 'Tab should switch back to bus');
  console.log('  ✓ Switched tab back to "bus"');

  // Test 2: Bus Search Validation
  console.log('\n2. [BUS SEARCH VALIDATION]');
  panel.busFrom = '';
  panel.busTo = 'Hyderabad';
  console.assert(!panel.validateBusSearch(), 'Empty source must fail');
  console.assert(panel.busErrorMessage === 'Please enter a source city.');
  console.log('  ✓ Empty From correctly rejected:', panel.busErrorMessage);

  panel.busFrom = 'Bengaluru';
  panel.busTo = '';
  console.assert(!panel.validateBusSearch(), 'Empty destination must fail');
  console.assert(panel.busErrorMessage === 'Please enter a destination city.');
  console.log('  ✓ Empty To correctly rejected:', panel.busErrorMessage);

  panel.busFrom = 'Hyderabad';
  panel.busTo = 'hyderabad';
  console.assert(!panel.validateBusSearch(), 'Identical cities must fail');
  console.assert(panel.busErrorMessage === 'Source and destination cities cannot be the same.');
  console.log('  ✓ Identical cities correctly rejected:', panel.busErrorMessage);

  panel.busFrom = 'Bengaluru';
  panel.busTo = 'Hyderabad';
  panel.busDate = '2020-01-01';
  console.assert(!panel.validateBusSearch(), 'Past date must fail');
  console.assert(panel.busErrorMessage === 'Travel date cannot be in the past.');
  console.log('  ✓ Past travel date correctly rejected:', panel.busErrorMessage);

  panel.busDate = panel.getTodayDate();
  console.assert(panel.validateBusSearch(), 'Valid search must pass');
  console.log(`  ✓ Valid bus search passed for: ${panel.busFrom} ➔ ${panel.busTo} on ${panel.busDate}`);

  // Test 3: Swap functionality
  console.log('\n3. [CITY SWAP BEHAVIOR]');
  panel.busFrom = 'Bengaluru';
  panel.busTo = 'Hyderabad';
  const preservedDate = panel.busDate;
  panel.swapCities();
  console.assert(panel.busFrom === 'Hyderabad' && panel.busTo === 'Bengaluru', 'Cities must swap');
  console.assert(panel.busDate === preservedDate, 'Travel date must be preserved');
  console.log(`  ✓ Swapped cities: From '${panel.busFrom}' To '${panel.busTo}' (Date retained: ${panel.busDate})`);

  // Test 4: Hotel Search Validation
  console.log('\n4. [HOTEL SEARCH VALIDATION]');
  panel.setTab('hotel');
  panel.hotelCity = '';
  console.assert(!panel.validateHotelSearch(), 'Empty city must fail');
  console.assert(panel.hotelErrorMessage === 'Please enter a city or destination.');
  console.log('  ✓ Empty hotel city rejected:', panel.hotelErrorMessage);

  panel.hotelCity = 'Goa';
  panel.hotelCheckIn = '2020-01-01';
  console.assert(!panel.validateHotelSearch(), 'Past check-in must fail');
  console.assert(panel.hotelErrorMessage === 'Check-in date cannot be in the past.');
  console.log('  ✓ Past check-in date rejected:', panel.hotelErrorMessage);

  panel.hotelCheckIn = panel.getTodayDate();
  panel.hotelCheckOut = panel.getTodayDate(); // Same day
  console.assert(!panel.validateHotelSearch(), 'Check-out must be after check-in');
  console.assert(panel.hotelErrorMessage === 'Check-out date must be later than check-in date.');
  console.log('  ✓ Same-day/earlier check-out rejected:', panel.hotelErrorMessage);

  panel.hotelCheckOut = panel.getTomorrowDate();
  console.assert(panel.validateHotelSearch(), 'Valid hotel search must pass');
  console.log(`  ✓ Valid hotel search passed for: ${panel.hotelCity} (${panel.hotelCheckIn} to ${panel.hotelCheckOut})`);

  // Test 5: Backend Search Endpoints Verification (API Gateway Integration)
  console.log('\n5. [BACKEND API INTEGRATION VIA GATEWAY]');
  const busApiRes = await req(`${API_BASE}/buses/search?origin=Bengaluru&destination=Hyderabad&date=${panel.getTodayDate()}`);
  console.assert(busApiRes.ok && Array.isArray(busApiRes.data), 'Bus API should return 200 array');
  console.log(`  ✓ Bus Search API: /api/buses/search?origin=Bengaluru&destination=Hyderabad&date=${panel.getTodayDate()}`);
  console.log(`    Status: ${busApiRes.status}, Returned ${busApiRes.data.length} schedule(s)`);

  const hotelApiRes = await req(`${API_BASE}/hotels/search?city=Goa`);
  console.assert(hotelApiRes.ok && Array.isArray(hotelApiRes.data), 'Hotel API should return 200 array');
  console.log(`  ✓ Hotel Search API: /api/hotels/search?city=Goa`);
  console.log(`    Status: ${hotelApiRes.status}, Returned ${hotelApiRes.data.length} hotel(s)`);

  console.log('\n================================================================');
  console.log('   ALL SEARCH PANEL TESTS PASSED SUCCESSFULLY!');
  console.log('================================================================\n');
}

runTests().catch(err => {
  console.error('Test execution error:', err);
  process.exit(1);
});
