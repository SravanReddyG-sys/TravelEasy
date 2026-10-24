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

async function getJson(endpoint, token, headerName = 'Authorization') {
  try {
    const headers = { 'Accept': 'application/json' };
    if (token) {
      if (headerName === 'Authorization') {
        headers['Authorization'] = `Bearer ${token}`;
      } else {
        headers[headerName] = token;
      }
    }

    const res = await fetch(`${API_BASE}${endpoint}`, {
      method: 'GET',
      headers
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
      headers: { 'Accept': 'application/json' }
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

async function runVerification() {
  console.log('================================================================');
  console.log('🧪 Starting Verification: Application Under Review & Application Details');
  console.log('================================================================\n');

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

  // -------------------------------------------------------------
  // TEST 1: Bus Operator Registration, Pending Status & Details
  // -------------------------------------------------------------
  console.log('--- Test 1: Bus Operator Registration, Login Check & Application Details ---');
  const busEmail = `bus_verify_${ts}@traveleasy.com`;
  const busPayload = {
    firstName: 'Vikram',
    lastName: 'Sharma',
    email: busEmail,
    phone: `+9197${String(ts).slice(-8)}`,
    password: 'Password123!',
    confirmPassword: 'Password123!',
    representativeDesignation: 'OPERATIONS_MANAGER',
    legalOrganizationName: 'Sharma Express Lines Pvt Ltd',
    businessStructure: 'Private Limited',
    passengerTransportPermitNumber: '887766554433',
    businessPanNumber: 'ABCDE5678F',
    gstinNumber: '29ABCDE5678F1Z5',
    registeredAddress: 'Building 4, Electronic City Phase 1, Bengaluru, Karnataka, 560100',
    businessContactEmail: 'operations@sharmaexpress.com',
    businessContactPhone: '+918044556677',
    termsAccepted: true
  };

  const regBus = await postJson('/auth/register/bus-operator', busPayload);
  assert(regBus.status === 201, `Bus operator registered with HTTP 201 (got ${regBus.status})`);
  assert(!regBus.body?.token, 'Registration does NOT issue a login JWT token');
  assert(Boolean(regBus.body?.applicationToken), 'Registration issues a scoped applicationToken');
  assert(regBus.body?.role === 'ROLE_BUS_OPERATOR', `Role is ROLE_BUS_OPERATOR`);
  assert(regBus.body?.status === 'PENDING', `Status is PENDING`);
  assert(regBus.body?.verificationStatus === 'UNDER_REVIEW', `Verification status is UNDER_REVIEW`);
  const busUserId = regBus.body?.id;
  const busRegAppToken = regBus.body?.applicationToken;

  // Confirm login is rejected and no JWT is issued
  const loginBus = await postJson('/auth/login', {
    email: busEmail,
    password: 'Password123!'
  });
  assert(loginBus.status === 403, `Pending login returns HTTP 403 Forbidden (got ${loginBus.status})`);
  assert(loginBus.body?.status === 'UNDER_REVIEW', `Login response status is UNDER_REVIEW`);
  assert(!loginBus.body?.token, 'Login does NOT issue a login JWT to pending operator');
  assert(Boolean(loginBus.body?.applicationToken), 'Login 403 response includes scoped applicationToken');
  const busLoginAppToken = loginBus.body?.applicationToken;

  // Retrieve application details using the scoped token
  const busDetailsRes = await getJson('/auth/application-details', busLoginAppToken);
  assert(busDetailsRes.status === 200, `GET /auth/application-details with applicationToken returns HTTP 200 OK`);
  const busDetails = busDetailsRes.body;
  assert(busDetails.organizationName === 'Sharma Express Lines Pvt Ltd', `organizationName is '${busDetails.organizationName}'`);
  assert(busDetails.applicationType === 'New Bus Service Registration', `applicationType is 'New Bus Service Registration'`);
  assert(busDetails.applicationStatus === 'UNDER_REVIEW', `applicationStatus is 'UNDER_REVIEW'`);
  assert(busDetails.firstName === 'Vikram', `firstName is 'Vikram'`);
  assert(busDetails.lastName === 'Sharma', `lastName is 'Sharma'`);
  assert(busDetails.email === busEmail, `email is '${busEmail}'`);
  assert(busDetails.phone === busPayload.phone, `phone is '${busPayload.phone}'`);
  assert(busDetails.representativeDesignation === 'OPERATIONS_MANAGER', `representativeDesignation is 'OPERATIONS_MANAGER'`);
  assert(busDetails.businessStructure === 'Private Limited', `businessStructure is 'Private Limited'`);
  assert(busDetails.licensePermitNumber === '887766554433', `licensePermitNumber is '887766554433'`);
  assert(busDetails.businessPanNumber === 'ABCDE5678F', `businessPanNumber is 'ABCDE5678F'`);
  assert(busDetails.gstinNumber === '29ABCDE5678F1Z5', `gstinNumber is '29ABCDE5678F1Z5'`);
  assert(busDetails.registeredAddress.includes('Electronic City'), `registeredAddress includes 'Electronic City'`);
  assert(busDetails.businessContactEmail === 'operations@sharmaexpress.com', `businessContactEmail matches`);
  assert(busDetails.businessContactPhone === '+918044556677', `businessContactPhone matches`);
  assert(Boolean(busDetails.submittedOn), `submittedOn timestamp is present (${busDetails.submittedOn})`);

  // Verify X-Application-Token header also works
  const busDetailsHeaderRes = await getJson('/auth/application-details', busRegAppToken, 'X-Application-Token');
  assert(busDetailsHeaderRes.status === 200, `GET /auth/application-details with X-Application-Token header returns HTTP 200 OK`);

  // -------------------------------------------------------------
  // TEST 2: Hotel Partner Registration & Flow (Optional GSTIN Absent)
  // -------------------------------------------------------------
  console.log('\n--- Test 2: Hotel Partner Registration (Optional GSTIN Omitted) & Flow ---');
  const hotelEmail = `hotel_verify_${ts}@traveleasy.com`;
  const hotelPayload = {
    firstName: 'Ananya',
    lastName: 'Roy',
    email: hotelEmail,
    phone: `+9198${String(ts).slice(-8)}`,
    password: 'Password123!',
    confirmPassword: 'Password123!',
    representativeDesignation: 'GENERAL_MANAGER',
    legalOrganizationName: 'Roy Royal Palms Hospitality Ltd',
    businessStructure: 'LLP',
    companyLicenseNumber: '998877665544',
    businessPanNumber: 'ROYAB1234K',
    // Optional GSTIN omitted to verify fallback handling
    registeredAddress: '15 Ocean Drive, Calangute, Goa, 403516',
    businessContactEmail: 'gm@royroyalpalms.com',
    businessContactPhone: '+918329988776',
    termsAccepted: true
  };

  const regHotel = await postJson('/auth/register/hotel-partner', hotelPayload);
  assert(regHotel.status === 201, `Hotel partner registered with HTTP 201 (got ${regHotel.status})`);
  assert(!regHotel.body?.token, 'Hotel registration does NOT issue a login JWT token');
  assert(Boolean(regHotel.body?.applicationToken), 'Hotel registration issues scoped applicationToken');
  assert(regHotel.body?.role === 'ROLE_HOTEL_MANAGER', `Role is ROLE_HOTEL_MANAGER`);
  const hotelUserId = regHotel.body?.id;
  const hotelAppToken = regHotel.body?.applicationToken;

  const loginHotel = await postJson('/auth/login', {
    email: hotelEmail,
    password: 'Password123!'
  });
  assert(loginHotel.status === 403, `Pending hotel login returns HTTP 403 Forbidden`);
  assert(loginHotel.body?.status === 'UNDER_REVIEW', `Hotel login response status is UNDER_REVIEW`);
  assert(!loginHotel.body?.token, 'Hotel login does NOT issue a login JWT');

  const hotelDetailsRes = await getJson('/auth/application-details', hotelAppToken);
  assert(hotelDetailsRes.status === 200, `GET /auth/application-details returns HTTP 200 OK for hotel partner`);
  const hotelDetails = hotelDetailsRes.body;
  assert(hotelDetails.organizationName === 'Roy Royal Palms Hospitality Ltd', `organizationName is '${hotelDetails.organizationName}'`);
  assert(hotelDetails.applicationType === 'New Hotel Partner Registration', `applicationType is 'New Hotel Partner Registration'`);
  assert(hotelDetails.representativeDesignation === 'GENERAL_MANAGER', `representativeDesignation is 'GENERAL_MANAGER'`);
  assert(hotelDetails.licensePermitNumber === '998877665544', `licensePermitNumber is '998877665544'`);
  assert(hotelDetails.gstinNumber == null, `Optional GSTIN is null/omitted as expected (got ${hotelDetails.gstinNumber})`);

  // -------------------------------------------------------------
  // TEST 3: Security & Privacy Isolation
  // -------------------------------------------------------------
  console.log('\n--- Test 3: Security & Privacy Boundary Checks ---');
  // 3a. Unauthenticated request without token rejected
  const unauthRes = await getJson('/auth/application-details', null);
  assert(unauthRes.status === 401, `Unauthenticated request without token rejected with HTTP 401 (got ${unauthRes.status})`);

  // 3b. Forged/invalid token rejected
  const invalidTokenRes = await getJson('/auth/application-details', 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.token');
  assert(invalidTokenRes.status === 401, `Invalid token rejected with HTTP 401 (got ${invalidTokenRes.status})`);

  // 3c. Bus token returns bus details; Hotel token returns hotel details (no cross-access)
  assert(busDetails.email === busEmail, 'Bus token strictly resolves to Bus Operator');
  assert(hotelDetails.email === hotelEmail, 'Hotel token strictly resolves to Hotel Partner');
  assert(busDetails.email !== hotelDetails.email, 'Applicants cannot access each others records');

  // -------------------------------------------------------------
  // TEST 4: Admin Approval -> Full Login Flow Restored
  // -------------------------------------------------------------
  console.log('\n--- Test 4: Admin Approves Bus Operator -> Normal Login Succeeds ---');
  const approveRes = await putJson(`/users/${busUserId}/verify?verified=true`);
  assert(approveRes.status === 200, `Admin approves bus operator (got ${approveRes.status})`);

  const approvedLogin = await postJson('/auth/login', {
    email: busEmail,
    password: 'Password123!'
  });
  assert(approvedLogin.status === 200, `Approved bus operator login succeeds with HTTP 200 OK (got ${approvedLogin.status})`);
  assert(Boolean(approvedLogin.body?.token), 'Approved user receives valid login JWT token');
  assert(approvedLogin.body?.status === 'ACTIVE', `Status transitioned to ACTIVE`);
  assert(approvedLogin.body?.verificationStatus === 'APPROVED', `VerificationStatus transitioned to APPROVED`);

  // Can also access application-details with the authenticated login JWT
  const detailsWithLoginJwt = await getJson('/auth/application-details', approvedLogin.body.token);
  assert(detailsWithLoginJwt.status === 200, `Application details accessible using approved login JWT`);
  assert(detailsWithLoginJwt.body?.applicationStatus === 'APPROVED', `Application status reflected as APPROVED`);

  console.log('\n================================================================');
  console.log(`End-to-End Test Suite Complete: ${passed}/${total} assertions passed!`);
  console.log('================================================================\n');
}

runVerification();
