import {
  AUTH_USER_STORAGE_KEY,
  BackendApplicationStatusSnapshot,
  decodeJwtPayload,
  isJwtSessionTokenValid,
  readAndValidateStoredSession,
  resolveDashboardRoute,
  resolvePostLoginRoute,
  resolveRevalidatedSessionUser,
  validateStoredUserSession
} from './auth-session.util';

function createMemoryStorage(): Storage {
  const store: Record<string, string> = {};
  return {
    get length() {
      return Object.keys(store).length;
    },
    clear() {
      Object.keys(store).forEach(key => delete store[key]);
    },
    getItem(key: string) {
      return Object.prototype.hasOwnProperty.call(store, key) ? store[key] : null;
    },
    key(index: number) {
      return Object.keys(store)[index] ?? null;
    },
    removeItem(key: string) {
      delete store[key];
    },
    setItem(key: string, value: string) {
      store[key] = String(value);
    }
  };
}

function encodeBase64Url(obj: Record<string, unknown>): string {
  const json = JSON.stringify(obj);
  if (typeof Buffer !== 'undefined') {
    return Buffer.from(json, 'utf-8').toString('base64url');
  }
  const bytes = new TextEncoder().encode(json);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function createSignedStyleJwt(claims: Record<string, unknown>, signature = 'test-signature'): string {
  const header = encodeBase64Url({ alg: 'HS256', typ: 'JWT' });
  const payload = encodeBase64Url(claims);
  return `${header}.${payload}.${signature}`;
}

describe('auth-session.util', () => {
  const nowMs = 1_800_000_000_000;
  const futureExpSec = Math.floor((nowMs + 3_600_000) / 1000);
  const pastExpSec = Math.floor((nowMs - 60_000) / 1000);

  it('restores valid authenticated sessions and maps all four roles to expected dashboards', () => {
    const cases: Array<{ role: 'ROLE_CUSTOMER' | 'ROLE_BUS_OPERATOR' | 'ROLE_HOTEL_MANAGER' | 'ROLE_ADMIN'; route: string; verified?: boolean; verificationStatus?: string }> = [
      { role: 'ROLE_CUSTOMER', route: '/customer', verified: true, verificationStatus: 'APPROVED' },
      { role: 'ROLE_BUS_OPERATOR', route: '/bus-operator', verified: true, verificationStatus: 'APPROVED' },
      { role: 'ROLE_HOTEL_MANAGER', route: '/hotel-manager', verified: true, verificationStatus: 'APPROVED' },
      { role: 'ROLE_ADMIN', route: '/admin', verified: true }
    ];

    for (const testCase of cases) {
      const storage = createMemoryStorage();
      const token = createSignedStyleJwt({
        sub: `${testCase.role.toLowerCase()}@traveleasy.com`,
        userId: 101,
        role: testCase.role,
        fullName: `User ${testCase.role}`,
        exp: futureExpSec
      });

      storage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify({
        id: 101,
        email: `${testCase.role.toLowerCase()}@traveleasy.com`,
        fullName: `User ${testCase.role}`,
        role: testCase.role,
        status: 'ACTIVE',
        verified: testCase.verified,
        verificationStatus: testCase.verificationStatus,
        token
      }));

      const restored = readAndValidateStoredSession(storage, nowMs);
      expect(restored).not.toBeNull();
      expect(restored?.role).toBe(testCase.role);
      expect(resolveDashboardRoute(restored)).toBe(testCase.route);
      expect(storage.getItem(AUTH_USER_STORAGE_KEY)).not.toBeNull();
    }
  });

  it('rejects and clears expired JWT tokens from storage', () => {
    const storage = createMemoryStorage();
    const expiredToken = createSignedStyleJwt({
      sub: 'customer@traveleasy.com',
      userId: 5,
      role: 'ROLE_CUSTOMER',
      fullName: 'John Doe',
      exp: pastExpSec
    });

    storage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify({
      id: 5,
      email: 'customer@traveleasy.com',
      fullName: 'John Doe',
      role: 'ROLE_CUSTOMER',
      status: 'ACTIVE',
      verified: true,
      token: expiredToken
    }));

    expect(isJwtSessionTokenValid(expiredToken, nowMs).valid).toBe(false);
    const restored = readAndValidateStoredSession(storage, nowMs);
    expect(restored).toBeNull();
    expect(storage.getItem(AUTH_USER_STORAGE_KEY)).toBeNull();
  });

  it('rejects and clears malformed JSON or corrupted non-object storage values', () => {
    const corruptedValues = [
      '{not-valid-json',
      '"just-a-string"',
      '[1,2,3]',
      'null',
      JSON.stringify({ id: 1, email: 'x@y.com', role: 'ROLE_CUSTOMER' })
    ];

    for (const raw of corruptedValues) {
      const storage = createMemoryStorage();
      storage.setItem(AUTH_USER_STORAGE_KEY, raw);

      const restored = readAndValidateStoredSession(storage, nowMs);
      expect(restored).toBeNull();
      expect(storage.getItem(AUTH_USER_STORAGE_KEY)).toBeNull();
    }
  });

  it('rejects application tracking tokens and unapproved/blocked partner accounts', () => {
    const appTrackingToken = createSignedStyleJwt({
      sub: 'partner@traveleasy.com',
      userId: 12,
      role: 'ROLE_BUS_OPERATOR',
      scope: 'APPLICATION_DETAILS',
      tokenType: 'APPLICATION_TRACKING',
      exp: futureExpSec
    });

    expect(isJwtSessionTokenValid(appTrackingToken, nowMs).valid).toBe(false);

    const validJwt = createSignedStyleJwt({
      sub: 'partner@traveleasy.com',
      userId: 12,
      role: 'ROLE_HOTEL_MANAGER',
      exp: futureExpSec
    });

    const unverifiedPartner = validateStoredUserSession({
      id: 12,
      email: 'partner@traveleasy.com',
      fullName: 'Pending Hotel',
      role: 'ROLE_HOTEL_MANAGER',
      status: 'PENDING',
      verificationStatus: 'UNDER_REVIEW',
      verified: false,
      token: validJwt
    }, nowMs);
    expect(unverifiedPartner.valid).toBe(false);

    const suspendedAdmin = validateStoredUserSession({
      id: 12,
      email: 'partner@traveleasy.com',
      fullName: 'Suspended User',
      role: 'ROLE_HOTEL_MANAGER',
      status: 'SUSPENDED',
      verificationStatus: 'APPROVED',
      verified: true,
      token: validJwt
    }, nowMs);
    expect(suspendedAdmin.valid).toBe(false);
  });

  it('rejects stored sessions whose role, id, or email disagree with signed JWT claims', () => {
    const customerJwt = createSignedStyleJwt({
      sub: 'customer@traveleasy.com',
      userId: 10,
      role: 'ROLE_CUSTOMER',
      fullName: 'Customer One',
      exp: futureExpSec
    });

    const roleEscalationAttempt = validateStoredUserSession({
      id: 10,
      email: 'customer@traveleasy.com',
      fullName: 'Customer One',
      role: 'ROLE_ADMIN',
      status: 'ACTIVE',
      token: customerJwt
    }, nowMs);
    expect(roleEscalationAttempt.valid).toBe(false);

    const mismatchedEmail = validateStoredUserSession({
      id: 10,
      email: 'other@traveleasy.com',
      fullName: 'Customer One',
      role: 'ROLE_CUSTOMER',
      status: 'ACTIVE',
      token: customerJwt
    }, nowMs);
    expect(mismatchedEmail.valid).toBe(false);

    const mismatchedId = validateStoredUserSession({
      id: 999,
      email: 'customer@traveleasy.com',
      fullName: 'Customer One',
      role: 'ROLE_CUSTOMER',
      status: 'ACTIVE',
      token: customerJwt
    }, nowMs);
    expect(mismatchedId.valid).toBe(false);
  });

  it('decodes UTF-8 full names inside JWT payloads properly', () => {
    const jwt = createSignedStyleJwt({
      sub: 'traveller@traveleasy.com',
      userId: 44,
      role: 'ROLE_CUSTOMER',
      fullName: 'José Álvares',
      exp: futureExpSec
    });

    const payload = decodeJwtPayload(jwt);
    expect(payload?.fullName).toBe('José Álvares');
  });

  it('resolves post-login navigation safely using role routes and safe returnUrl filtering', () => {
    expect(resolvePostLoginRoute({ role: 'ROLE_CUSTOMER', status: 'ACTIVE' })).toBe('/customer');
    expect(resolvePostLoginRoute({ role: 'ROLE_BUS_OPERATOR', status: 'ACTIVE', verificationStatus: 'APPROVED' })).toBe('/bus-operator');
    expect(resolvePostLoginRoute({ role: 'ROLE_HOTEL_MANAGER', status: 'ACTIVE', verificationStatus: 'APPROVED' })).toBe('/hotel-manager');
    expect(resolvePostLoginRoute({ role: 'ROLE_ADMIN', status: 'ACTIVE' })).toBe('/admin');
    expect(resolvePostLoginRoute({ role: 'ROLE_BUS_OPERATOR', status: 'PENDING', verificationStatus: 'UNDER_REVIEW' })).toBe('/account-pending');

    // Deep link allowed for role
    expect(resolvePostLoginRoute({ role: 'ROLE_BUS_OPERATOR', status: 'ACTIVE' }, '/bus-operator/buses/5')).toBe('/bus-operator/buses/5');
    // Disallowed cross-role returnUrl falls back to user's own dashboard
    expect(resolvePostLoginRoute({ role: 'ROLE_CUSTOMER', status: 'ACTIVE' }, '/admin')).toBe('/customer');
    // Protocol-relative open redirect blocked
    expect(resolvePostLoginRoute({ role: 'ROLE_ADMIN', status: 'ACTIVE' }, '//evil.example')).toBe('/admin');
  });

  it('revalidates server snapshot without breaking seeded admin lacking explicit verificationStatus', () => {
    const adminUser = {
      id: 1,
      email: 'admin@traveleasy.com',
      fullName: 'Platform Administrator',
      role: 'ROLE_ADMIN' as const,
      status: 'ACTIVE',
      verified: true,
      token: createSignedStyleJwt({
        sub: 'admin@traveleasy.com',
        userId: 1,
        role: 'ROLE_ADMIN',
        exp: futureExpSec
      })
    };

    const adminSnapshotFromBackend: BackendApplicationStatusSnapshot = {
      role: 'ROLE_ADMIN',
      accountStatus: 'ACTIVE',
      applicationStatus: 'UNDER_REVIEW',
      verificationStatus: 'UNDER_REVIEW',
      verified: true,
      fullName: 'Platform Administrator'
    };

    const adminResult = resolveRevalidatedSessionUser(adminUser, adminSnapshotFromBackend);
    expect(adminResult.valid).toBe(true);

    const suspendedPartnerResult = resolveRevalidatedSessionUser(
      { ...adminUser, role: 'ROLE_BUS_OPERATOR', verificationStatus: 'APPROVED' },
      { role: 'ROLE_BUS_OPERATOR', accountStatus: 'SUSPENDED', verified: true }
    );
    expect(suspendedPartnerResult.valid).toBe(false);
    expect(suspendedPartnerResult.redirectTo).toBe('/account-suspended');
  });
});
