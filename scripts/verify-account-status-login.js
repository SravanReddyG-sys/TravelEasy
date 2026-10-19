const API_BASE = 'http://localhost:8080/api';

async function postJson(endpoint, payload) {
  try {
    const res = await fetch(`${API_BASE}${endpoint}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    const text = await res.text();
    let body;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      body = text;
    }

    return { status: res.status, ok: res.ok, body };
  } catch (err) {
    return { status: 0, ok: false, body: err.message };
  }
}

async function putJson(endpoint) {
  try {
    const res = await fetch(`${API_BASE}${endpoint}`, {
      method: 'PUT',
      headers: {
        'Accept': 'application/json'
      }
    });

    const text = await res.text();
    let body;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      body = text;
    }

    return { status: res.status, ok: res.ok, body };
  } catch (err) {
    return { status: 0, ok: false, body: err.message };
  }
}

async function run() {
  console.log('🧪 Starting Full End-to-End Account Status & Verification Verification...\n');
  let passed = 0;
  let total = 0;

  function assert(condition, message) {
    total++;
    if (condition) {
      console.log(`  ✓ [PASS] ${message}`);
      passed++;
    } else {
      console.error(`  ✗ [FAIL] ${message}`);
      process.exitCode = 1;
    }
  }

  const ts = Date.now();

  // SCENARIO 1: Valid Traveller Login
  console.log('--- Scenario 1: Valid Traveller Registration & Login ---');
  const travellerEmail = `traveller_live_${ts}@traveleasy.com`;
  const regTraveller = await postJson('/auth/register/traveller', {
    firstName: 'Alice',
    lastName: 'Traveller',
    email: travellerEmail,
    phone: `+9196${String(ts).slice(-8)}`,
    password: 'Password123!',
    confirmPassword: 'Password123!',
    termsAccepted: true
  });
  assert(regTraveller.status === 201, `Traveller registered with 201 Created (got ${regTraveller.status})`);

  const loginTraveller = await postJson('/auth/login', {
    email: travellerEmail,
    password: 'Password123!'
  });
  assert(loginTraveller.status === 200, `Active traveller login returns HTTP 200 OK (got ${loginTraveller.status})`);
  assert(Boolean(loginTraveller.body?.token), 'Traveller received valid JWT token');
  assert(loginTraveller.body?.role === 'ROLE_CUSTOMER', `Role is ROLE_CUSTOMER (got ${loginTraveller.body?.role})`);

  // SCENARIO 2: Pending Bus Operator Under Review
  console.log('\n--- Scenario 2: Pending Bus Operator Under Review ---');
  const busEmail = `bus_op_live_${ts}@traveleasy.com`;
  const regBus = await postJson('/auth/register/bus-operator', {
    firstName: 'Fleet',
    lastName: 'Lead',
    email: busEmail,
    phone: `+9195${String(ts).slice(-8)}`,
    password: 'Password123!',
    confirmPassword: 'Password123!',
    representativeDesignation: 'Transport Manager',
    legalOrganizationName: 'Live Travels Express Ltd',
    businessStructure: 'Private Limited',
    passengerTransportPermitNumber: '123456789012',
    businessPanNumber: 'ABCDE1234F',
    registeredAddress: '100 Hub Road, Bengaluru',
    businessContactEmail: 'contact@express.com',
    businessContactPhone: '+918012345678',
    termsAccepted: true
  });
  assert(regBus.status === 201, `Bus operator registered with PENDING status (got ${regBus.status})`);
  const busUserId = regBus.body?.id;

  const loginPendingBus = await postJson('/auth/login', {
    email: busEmail,
    password: 'Password123!'
  });
  assert(loginPendingBus.status === 403, `Pending bus operator login rejected with HTTP 403 Forbidden (got ${loginPendingBus.status})`);
  assert(loginPendingBus.body?.status === 'UNDER_REVIEW', `Response status code is UNDER_REVIEW (got ${loginPendingBus.body?.status})`);
  assert(loginPendingBus.body?.message === 'Your application is currently under review.', `Message is friendly under review explanation`);
  assert(!loginPendingBus.body?.token, 'Crucial security assertion: NO JWT token issued to pending bus operator');

  // SCENARIO 3: Pending Hotel Partner Under Review
  console.log('\n--- Scenario 3: Pending Hotel Partner Under Review ---');
  const hotelEmail = `hotel_mgr_live_${ts}@traveleasy.com`;
  const regHotel = await postJson('/auth/register/hotel-partner', {
    firstName: 'Sarah',
    lastName: 'Manager',
    email: hotelEmail,
    phone: `+9194${String(ts).slice(-8)}`,
    password: 'Password123!',
    confirmPassword: 'Password123!',
    representativeDesignation: 'General Manager',
    legalOrganizationName: 'Grand Horizon Resorts',
    businessStructure: 'LLP',
    companyLicenseNumber: '987654321098',
    businessPanNumber: 'XYZAB5678G',
    registeredAddress: '25 Beach Road, Goa',
    businessContactEmail: 'reservations@horizon.com',
    businessContactPhone: '+918321234567',
    termsAccepted: true
  });
  assert(regHotel.status === 201, `Hotel partner registered with PENDING status (got ${regHotel.status})`);
  const hotelUserId = regHotel.body?.id;

  const loginPendingHotel = await postJson('/auth/login', {
    email: hotelEmail,
    password: 'Password123!'
  });
  assert(loginPendingHotel.status === 403, `Pending hotel partner login rejected with HTTP 403 Forbidden (got ${loginPendingHotel.status})`);
  assert(loginPendingHotel.body?.status === 'UNDER_REVIEW', `Response status code is UNDER_REVIEW (got ${loginPendingHotel.body?.status})`);
  assert(!loginPendingHotel.body?.token, 'Crucial security assertion: NO JWT token issued to pending hotel partner');

  // SCENARIO 4: Approved Bus Operator Login
  console.log('\n--- Scenario 4: Admin Approves Bus Operator -> Login Succeeds ---');
  const approveBus = await putJson(`/users/${busUserId}/verify?verified=true`);
  assert(approveBus.status === 200, `Admin verified bus operator (got ${approveBus.status})`);

  const loginApprovedBus = await postJson('/auth/login', {
    email: busEmail,
    password: 'Password123!'
  });
  assert(loginApprovedBus.status === 200, `Approved bus operator login succeeds with HTTP 200 OK (got ${loginApprovedBus.status})`);
  assert(Boolean(loginApprovedBus.body?.token), 'Approved bus operator receives valid JWT token');
  assert(loginApprovedBus.body?.role === 'ROLE_BUS_OPERATOR', `Role is ROLE_BUS_OPERATOR (got ${loginApprovedBus.body?.role})`);
  assert(loginApprovedBus.body?.status === 'ACTIVE', `Status is ACTIVE (got ${loginApprovedBus.body?.status})`);
  assert(loginApprovedBus.body?.verificationStatus === 'APPROVED', `Verification is APPROVED (got ${loginApprovedBus.body?.verificationStatus})`);

  // SCENARIO 5: Rejected Hotel Partner Login
  console.log('\n--- Scenario 5: Admin Rejects Hotel Partner -> Login Fails Closed ---');
  const rejectHotel = await putJson(`/users/${hotelUserId}/verify?verified=false`);
  assert(rejectHotel.status === 200, `Admin marked hotel partner rejected (got ${rejectHotel.status})`);

  const loginRejectedHotel = await postJson('/auth/login', {
    email: hotelEmail,
    password: 'Password123!'
  });
  assert(loginRejectedHotel.status === 403, `Rejected hotel partner login returns HTTP 403 (got ${loginRejectedHotel.status})`);
  assert(loginRejectedHotel.body?.status === 'REJECTED', `Response status code is REJECTED (got ${loginRejectedHotel.body?.status})`);
  assert(!loginRejectedHotel.body?.token, 'NO JWT token issued to rejected hotel partner');

  // SCENARIO 6: Invalid Password
  console.log('\n--- Scenario 6: Invalid Password ---');
  const invalidPass = await postJson('/auth/login', {
    email: travellerEmail,
    password: 'WrongPassword999!'
  });
  assert(invalidPass.status === 401, `Invalid password returns HTTP 401 Unauthorized (got ${invalidPass.status})`);
  assert(!invalidPass.body?.token, 'NO JWT token issued for invalid password');

  console.log(`\n======================================================`);
  console.log(`End-to-End Live Verification Results: ${passed}/${total} passed!`);
  console.log(`======================================================\n`);
}

run();
