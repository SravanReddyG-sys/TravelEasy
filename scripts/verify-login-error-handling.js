/**
 * Travel Easy - Login Error Handling Verification Script
 * ======================================================
 * Verifies that invalid credentials return 401 and map to the friendly message:
 * "Invalid email or password. Please provide valid credentials and try again."
 * without exposing technical Angular HttpErrorResponse strings.
 */

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

    return {
      status: res.status,
      ok: res.ok,
      body
    };
  } catch (err) {
    return {
      status: 0,
      ok: false,
      body: err.message
    };
  }
}

// Emulates the exact error-handling logic from LoginComponent
class MockLoginComponent {
  constructor() {
    this.email = '';
    this.password = '';
    this.loading = false;
    this.errorMessage = '';
    this.redirectedTo = null;
  }

  clearError() {
    if (this.errorMessage) {
      this.errorMessage = '';
    }
  }

  handleLoginError(err) {
    this.loading = false;
    const errObj = err.error;

    // Account status checks (403 Forbidden redirects)
    if (errObj && typeof errObj === 'object') {
      if (errObj.status === 'SUSPENDED') {
        this.redirectedTo = '/account-suspended';
        return;
      }
      if (errObj.status === 'REJECTED') {
        this.redirectedTo = '/account-rejected';
        return;
      }
      if (errObj.status === 'UNDER_REVIEW' || errObj.status === 'PENDING') {
        this.redirectedTo = '/account-pending';
        return;
      }
    }

    // Categorize HTTP status and display clean, user-friendly messages
    if (err.status === 401) {
      this.errorMessage = 'Invalid email or password. Please provide valid credentials and try again.';
    } else if (err.status === 403) {
      this.errorMessage = (typeof errObj === 'object' && errObj?.message)
        ? errObj.message
        : 'Access denied. Your account is not authorized to log in.';
    } else if (err.status === 400) {
      this.errorMessage = (typeof errObj === 'object' && errObj?.message)
        ? errObj.message
        : 'Invalid request. Please check your email and password and try again.';
    } else if (err.status === 429) {
      this.errorMessage = 'Too many failed login attempts. Please wait a few minutes and try again.';
    } else if (err.status === 0) {
      this.errorMessage = 'Unable to connect to the server. Please check your connection and try again.';
    } else if (err.status >= 500) {
      this.errorMessage = 'Something went wrong on the server. Please try again later.';
    } else if (typeof errObj === 'object' && errObj?.message && typeof errObj.message === 'string') {
      this.errorMessage = errObj.message;
    } else {
      this.errorMessage = 'Invalid email or password. Please provide valid credentials and try again.';
    }
  }

  handleLoginSuccess(user) {
    this.loading = false;
    if (user.status === 'PENDING' || user.verificationStatus === 'UNDER_REVIEW') {
      this.redirectedTo = '/account-pending';
      return;
    }

    switch (user.role) {
      case 'ROLE_CUSTOMER': this.redirectedTo = '/customer'; break;
      case 'ROLE_BUS_OPERATOR': this.redirectedTo = '/bus-operator'; break;
      case 'ROLE_HOTEL_MANAGER': this.redirectedTo = '/hotel-manager'; break;
      case 'ROLE_ADMIN': this.redirectedTo = '/admin'; break;
      default: this.redirectedTo = '/';
    }
  }
}

async function runTests() {
  console.log('🧪 Starting Travel Easy Login Error Handling Verification...\n');
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

  // TEST 1: Backend returns 401 for incorrect password on existing user
  console.log('--- Test 1: Submitting Incorrect Password for Existing User ---');
  const res1 = await postJson('/auth/login', {
    email: 'customer@traveleasy.com',
    password: 'incorrectPassword123!'
  });
  assert(res1.status === 401, `Backend returned HTTP 401 for incorrect password (got ${res1.status})`);

  const comp1 = new MockLoginComponent();
  comp1.email = 'customer@traveleasy.com';
  comp1.password = 'incorrectPassword123!';
  comp1.handleLoginError({
    status: res1.status,
    error: res1.body,
    message: 'Http failure response for http://localhost:8080/api/auth/login: 401 OK'
  });

  assert(
    comp1.errorMessage === 'Invalid email or password. Please provide valid credentials and try again.',
    'UI displays friendly invalid credentials message'
  );
  assert(
    !comp1.errorMessage.includes('Http failure response'),
    'Raw HTTP error response string is NOT exposed'
  );
  assert(
    !comp1.errorMessage.includes('http://localhost:8080'),
    'Backend URL is NOT exposed'
  );
  assert(
    comp1.email === 'customer@traveleasy.com',
    'User email address remains populated after failure'
  );
  assert(
    comp1.password === 'incorrectPassword123!',
    'User password remains populated so user can correct typos'
  );

  // TEST 2: Backend returns 401 for non-existent account
  console.log('\n--- Test 2: Submitting Non-existent Account ---');
  const res2 = await postJson('/auth/login', {
    email: 'nonexistent_user_999@traveleasy.com',
    password: 'SomePassword123!'
  });
  assert(res2.status === 401, `Backend returned HTTP 401 for unknown user (got ${res2.status})`);

  const comp2 = new MockLoginComponent();
  comp2.email = 'nonexistent_user_999@traveleasy.com';
  comp2.handleLoginError({
    status: res2.status,
    error: res2.body,
    message: 'Http failure response for http://localhost:8080/api/auth/login: 401 OK'
  });
  assert(
    comp2.errorMessage === 'Invalid email or password. Please provide valid credentials and try again.',
    'Non-existent account yields identical friendly message without leaking account existence'
  );

  // TEST 3: Clearing errors on user input
  console.log('\n--- Test 3: Clear Error on Typing ---');
  assert(comp1.errorMessage !== '', 'Component has active error message');
  comp1.clearError();
  assert(comp1.errorMessage === '', 'clearError() immediately dismissed error message on input event');

  // TEST 4: Other HTTP Error Handlers
  console.log('\n--- Test 4: Other HTTP Status Code Mappings ---');
  const compOther = new MockLoginComponent();

  // 400 Bad Request
  compOther.handleLoginError({ status: 400, error: { message: 'Missing credentials' } });
  assert(compOther.errorMessage === 'Missing credentials', '400 Bad Request displays validation error');

  // 403 Suspended Redirect
  compOther.handleLoginError({ status: 403, error: { status: 'SUSPENDED' } });
  assert(compOther.redirectedTo === '/account-suspended', '403 SUSPENDED redirects to /account-suspended');

  // 403 Rejected Redirect
  compOther.handleLoginError({ status: 403, error: { status: 'REJECTED' } });
  assert(compOther.redirectedTo === '/account-rejected', '403 REJECTED redirects to /account-rejected');

  // 403 Under Review Redirect
  compOther.handleLoginError({ status: 403, error: { status: 'UNDER_REVIEW' } });
  assert(compOther.redirectedTo === '/account-pending', '403 UNDER_REVIEW redirects to /account-pending');

  // 403 Deactivated message
  compOther.handleLoginError({ status: 403, error: { status: 'DEACTIVATED', message: 'Your account has been deactivated. Please contact support.' } });
  assert(compOther.errorMessage === 'Your account has been deactivated. Please contact support.', '403 DEACTIVATED displays contact support message');

  // 429 Rate Limit
  compOther.handleLoginError({ status: 429, error: null });
  assert(compOther.errorMessage.includes('Too many failed login attempts'), '429 displays rate limit warning');

  // 0 Network / Connection Error
  compOther.handleLoginError({ status: 0, error: null });
  assert(compOther.errorMessage.includes('Unable to connect to the server'), '0 (offline) displays connection failure message');

  // 500 Internal Server Error
  compOther.handleLoginError({ status: 500, error: 'Internal Server Error' });
  assert(compOther.errorMessage === 'Something went wrong on the server. Please try again later.', '500 displays server error message');

  // TEST 5: Successful Login & Role Navigation
  console.log('\n--- Test 5: Successful Login Flow ---');
  const testEmail = `verify_login_${Date.now()}@traveleasy.com`;
  const regRes = await postJson('/auth/register/traveller', {
    firstName: 'Alice',
    lastName: 'Traveller',
    email: testEmail,
    phone: `+9198${String(Date.now()).slice(-8)}`,
    password: 'Password123!',
    confirmPassword: 'Password123!',
    termsAccepted: true
  });
  assert(regRes.status === 201, `Created new traveller account for login test (${regRes.status})`);

  const loginRes = await postJson('/auth/login', {
    email: testEmail,
    password: 'Password123!'
  });
  assert(loginRes.status === 200, `Login with correct credentials returns 200 OK (${loginRes.status})`);
  assert(Boolean(loginRes.body?.token), 'Response contains valid JWT token');
  assert(loginRes.body?.role === 'ROLE_CUSTOMER', `Response has role ROLE_CUSTOMER (got ${loginRes.body?.role})`);

  const compSuccess = new MockLoginComponent();
  compSuccess.handleLoginSuccess(loginRes.body);
  assert(compSuccess.redirectedTo === '/customer', 'ROLE_CUSTOMER routes correctly to /customer');

  console.log(`\n========================================`);
  console.log(`Results: ${passed}/${total} assertions passed!`);
  console.log(`========================================\n`);
}

runTests();
