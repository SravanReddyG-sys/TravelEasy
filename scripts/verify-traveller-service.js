/**
 * Travel Easy - Traveller Service Verification Script
 * ===================================================
 * Verifies that traveller-service is registered through the API Gateway,
 * enforces JWT authentication, provides CRUD operations for profile, preferences,
 * favourites, and support tickets, aggregates dashboard metrics, and enforces
 * cross-customer data isolation in booking-service.
 */

const API_BASE = process.env.API_GATEWAY_URL || 'http://localhost:8080/api';

async function req(endpoint, method = 'GET', body = null, token = null, extraHeaders = {}) {
  const headers = {
    'Accept': 'application/json',
    ...extraHeaders
  };
  if (body) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  try {
    const res = await fetch(`${API_BASE}${endpoint}`, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined
    });

    const text = await res.text();
    let data;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = text;
    }

    return { status: res.status, ok: res.ok, data };
  } catch (err) {
    return { status: 0, ok: false, error: err.message };
  }
}

async function verifyTravellerService() {
  console.log('🧪 Starting Traveller Service End-to-End Verification...\n');
  let passed = 0;
  let total = 0;

  function assert(condition, message) {
    total++;
    if (condition) {
      console.log(`  ✓ [PASS] ${message}`);
      passed++;
    } else {
      console.error(`  ✗ [FAIL] ${message}`);
    }
  }

  // --- TEST 1: Public Health Check ---
  console.log('📡 [TEST 1] Public Health Endpoint:');
  const healthRes = await req('/traveller/health');
  assert(healthRes.status === 200, `Health endpoint returns 200 OK (got ${healthRes.status})`);
  assert(healthRes.data?.service === 'traveller-service', `Service identifier is traveller-service`);

  // --- TEST 2: Unauthenticated Access Rejection ---
  console.log('\n🔒 [TEST 2] Authentication Enforcement:');
  const unauthProfile = await req('/traveller/profile');
  assert(unauthProfile.status === 401, `Unauthenticated /traveller/profile returns 401 Unauthorized (got ${unauthProfile.status})`);

  const unauthDash = await req('/traveller/dashboard');
  assert(unauthDash.status === 401, `Unauthenticated /traveller/dashboard returns 401 Unauthorized (got ${unauthDash.status})`);

  // --- TEST 3: Login to obtain customer token ---
  console.log('\n🔑 [TEST 3] Authentication & Token Acquisition:');
  const loginRes = await req('/auth/login', 'POST', {
    email: 'customer@traveleasy.com',
    password: 'Password@123'
  });

  let token = null;
  let customerId = null;

  if (loginRes.ok && loginRes.data?.token) {
    token = loginRes.data.token;
    customerId = loginRes.data.id;
    console.log(`  ✓ Customer login successful: ${loginRes.data.fullName} (ID: ${customerId})`);
  } else {
    // Try registering customer if not exists
    console.log('  ⚠️ Customer login failed, attempting registration...');
    const regRes = await req('/auth/register/traveller', 'POST', {
      firstName: 'Test',
      lastName: 'Traveller',
      email: 'test.traveller@traveleasy.com',
      phone: '+919876543299',
      password: 'Password@123',
      confirmPassword: 'Password@123'
    });
    if (regRes.ok && regRes.data?.token) {
      token = regRes.data.token;
      customerId = regRes.data.id;
      console.log(`  ✓ Test customer registered successfully (ID: ${customerId})`);
    } else {
      console.error('  ❌ Unable to acquire customer JWT token. Aborting authenticated tests.');
      return;
    }
  }

  // --- TEST 4: Profile Management ---
  console.log('\n👤 [TEST 4] Traveller Profile Management:');
  const getProfileRes = await req('/traveller/profile', 'GET', null, token);
  assert(getProfileRes.status === 200, `Fetch profile returns 200 OK (got ${getProfileRes.status})`);
  assert(getProfileRes.data?.userId === customerId, `Profile matches authenticated userId (${customerId})`);

  const updateProfileRes = await req('/traveller/profile', 'PUT', {
    city: 'Vijayawada',
    state: 'Andhra Pradesh',
    address: '45-12 Benz Circle',
    pincode: '520010',
    emergencyContactName: 'Jane Doe',
    emergencyContactPhone: '+919988776655',
    dateOfBirth: '1996-08-15',
    gender: 'MALE',
    idProofType: 'AADHAAR',
    idProofNumber: '1234-5678-9012'
  }, token);

  assert(updateProfileRes.status === 200, `Update profile returns 200 OK`);
  assert(updateProfileRes.data?.city === 'Vijayawada', `Updated city verified as Vijayawada`);
  assert(updateProfileRes.data?.emergencyContactName === 'Jane Doe', `Emergency contact name verified`);
  assert(updateProfileRes.data?.profileComplete === true, `Profile completion status is marked complete`);

  // --- TEST 5: Travel Preferences ---
  console.log('\n⚙️ [TEST 5] Traveller Preferences:');
  const getPrefRes = await req('/traveller/preferences', 'GET', null, token);
  assert(getPrefRes.status === 200, `Fetch preferences returns 200 OK`);

  const updatePrefRes = await req('/traveller/preferences', 'PUT', {
    seatPreference: 'WINDOW',
    berthPreference: 'LOWER',
    mealPreference: 'VEG',
    preferredCurrency: 'INR',
    emailNotifications: true,
    smsNotifications: false
  }, token);
  assert(updatePrefRes.status === 200, `Update preferences returns 200 OK`);
  assert(updatePrefRes.data?.seatPreference === 'WINDOW', `Seat preference updated to WINDOW`);
  assert(updatePrefRes.data?.smsNotifications === false, `SMS notifications toggle updated to false`);

  // --- TEST 6: Favourites Management ---
  console.log('\n❤️ [TEST 6] Favourites & Saved Items:');
  const addFavRes = await req('/traveller/favourites', 'POST', {
    itemType: 'BUS',
    itemId: 10,
    title: 'GSR Travels Volvo Multi-Axle',
    subtitle: 'Vijayawada to Hyderabad',
    metadataJson: JSON.stringify({ fare: 650, travelDate: '2026-10-25' })
  }, token);
  assert(addFavRes.status === 201, `Add favourite returns 201 Created (got ${addFavRes.status})`);
  const favId = addFavRes.data?.id;

  const listFavRes = await req('/traveller/favourites', 'GET', null, token);
  assert(listFavRes.status === 200, `List favourites returns 200 OK`);
  assert(Array.isArray(listFavRes.data) && listFavRes.data.length > 0, `Favourites array contains saved items`);

  // --- TEST 7: Support Tickets ---
  console.log('\n🎫 [TEST 7] Support Tickets:');
  const createTicketRes = await req('/traveller/support/tickets', 'POST', {
    category: 'BOOKING',
    subject: 'Seat modification request',
    message: 'Can I swap to a window seat on my upcoming journey?'
  }, token);
  assert(createTicketRes.status === 201, `Create support ticket returns 201 Created`);
  assert(createTicketRes.data?.ticketRef?.startsWith('TKT-'), `Ticket ref generated with TKT- prefix`);
  assert(createTicketRes.data?.status === 'OPEN', `Ticket initial status is OPEN`);

  const listTicketsRes = await req('/traveller/support/tickets', 'GET', null, token);
  assert(listTicketsRes.status === 200, `List tickets returns 200 OK`);
  assert(Array.isArray(listTicketsRes.data) && listTicketsRes.data.length > 0, `Tickets list contains created ticket`);

  // --- TEST 8: Dashboard Aggregation ---
  console.log('\n📊 [TEST 8] Dashboard Aggregation Endpoint:');
  const dashRes = await req('/traveller/dashboard', 'GET', null, token);
  assert(dashRes.status === 200, `Dashboard endpoint returns 200 OK`);
  assert(dashRes.data?.userId === customerId, `Dashboard userId matches authenticated traveller`);
  assert(dashRes.data?.profileComplete === true, `Profile completion reflects updated profile`);
  assert(dashRes.data?.favouritesCount >= 1, `Favourites count reflects added favourites (${dashRes.data?.favouritesCount})`);
  assert(dashRes.data?.openTicketsCount >= 1, `Open tickets count reflects active tickets (${dashRes.data?.openTicketsCount})`);
  assert(Array.isArray(dashRes.data?.recentBookings), `Recent bookings array returned`);

  // --- TEST 9: Remove Favourite ---
  console.log('\n🗑️ [TEST 9] Delete Favourite Item:');
  if (favId) {
    const delFavRes = await req(`/traveller/favourites/${favId}`, 'DELETE', null, token);
    assert(delFavRes.status === 200, `Delete favourite returns 200 OK (got ${delFavRes.status})`);
  }

  // --- TEST 10: Security Data Isolation in Booking Service ---
  console.log('\n🛡️ [TEST 10] Cross-Customer Data Isolation in Booking Service:');
  // Attempt to fetch another customer's bookings (e.g. customer 9999) using caller's customer token
  const spoofBkgRes = await req('/bookings/customer/9999', 'GET', null, token);
  assert(spoofBkgRes.status === 403, `Accessing another customer's bookings with unprivileged token returns 403 Forbidden (got ${spoofBkgRes.status})`);

  // Fetching own bookings with valid token
  const ownBkgRes = await req(`/bookings/customer/${customerId}`, 'GET', null, token);
  assert(ownBkgRes.status === 200, `Accessing own customer bookings with valid token returns 200 OK (got ${ownBkgRes.status})`);

  console.log(`\n==================================================`);
  console.log(`Verification Summary: ${passed} / ${total} tests passed.`);
  console.log(`==================================================\n`);

  if (passed === total) {
    console.log('🎉 ALL TRAVELLER MICROSERVICE INTEGRATION TESTS PASSED SUCCESSFULLY!\n');
  } else {
    console.warn(`⚠️ Some tests failed (${total - passed} failures). Please review the logs above.\n`);
  }
}

verifyTravellerService().catch(err => {
  console.error('❌ Verification script encountered an unexpected error:', err);
  process.exit(1);
});
