import { Role, User } from '../../models/user.model';

export const AUTH_USER_STORAGE_KEY = 'traveleasy_user';

export const VALID_USER_ROLES: readonly Role[] = [
  'ROLE_CUSTOMER',
  'ROLE_BUS_OPERATOR',
  'ROLE_HOTEL_MANAGER',
  'ROLE_ADMIN'
];

export const ROLE_DASHBOARD_ROUTES: Readonly<Record<Role, string>> = {
  ROLE_CUSTOMER: '/customer',
  ROLE_BUS_OPERATOR: '/bus-operator',
  ROLE_HOTEL_MANAGER: '/hotel-manager',
  ROLE_ADMIN: '/admin'
};

const BLOCKED_ACCOUNT_STATUSES = new Set([
  'PENDING',
  'SUSPENDED',
  'DEACTIVATED',
  'REJECTED',
  'INACTIVE',
  'FORBIDDEN'
]);

const CLOCK_SKEW_TOLERANCE_MS = 60_000;

export interface JwtTokenClaims {
  sub?: string;
  userId?: number | string;
  role?: string;
  fullName?: string;
  exp?: number;
  iat?: number;
  nbf?: number;
  scope?: string;
  tokenType?: string;
  [key: string]: unknown;
}

export interface JwtValidationResult {
  valid: boolean;
  reason?: string;
  payload?: JwtTokenClaims;
}

export interface StoredSessionValidationResult {
  valid: boolean;
  user: User | null;
  reason?: string;
}

export interface BackendApplicationStatusSnapshot {
  role?: string;
  accountStatus?: string;
  status?: string;
  verificationStatus?: string;
  applicationStatus?: string;
  verified?: boolean;
  fullName?: string;
}

export interface SessionRevalidationResult {
  valid: boolean;
  redirectTo?: string;
  updatedUser?: User;
  reason?: string;
}

function decodeBase64UrlSegment(segment: string): string | null {
  if (!segment || typeof segment !== 'string') {
    return null;
  }

  let base64 = segment.replace(/-/g, '+').replace(/_/g, '/');
  const remainder = base64.length % 4;
  if (remainder === 1) {
    return null;
  }
  if (remainder > 0) {
    base64 += '='.repeat(4 - remainder);
  }

  try {
    let binary: string;
    if (typeof atob === 'function') {
      binary = atob(base64);
    } else if (typeof Buffer !== 'undefined') {
      binary = Buffer.from(base64, 'base64').toString('binary');
    } else {
      return null;
    }

    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }

    if (typeof TextDecoder !== 'undefined') {
      return new TextDecoder('utf-8', { fatal: false }).decode(bytes);
    }

    let ascii = '';
    for (let i = 0; i < bytes.length; i++) {
      ascii += String.fromCharCode(bytes[i]);
    }
    return decodeURIComponent(escape(ascii));
  } catch {
    return null;
  }
}

function parseJsonObject(jsonText: string | null): Record<string, unknown> | null {
  if (!jsonText) {
    return null;
  }
  try {
    const parsed: unknown = JSON.parse(jsonText);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return null;
    }
    return parsed as Record<string, unknown>;
  } catch {
    return null;
  }
}

export function isValidUserRole(role: unknown): role is Role {
  return typeof role === 'string' && (VALID_USER_ROLES as readonly string[]).includes(role);
}

export function decodeJwtPayload(token: string): JwtTokenClaims | null {
  if (typeof token !== 'string') {
    return null;
  }

  const trimmed = token.trim();
  const parts = trimmed.split('.');
  if (parts.length !== 3 || !parts[0] || !parts[1] || !parts[2]) {
    return null;
  }

  const header = parseJsonObject(decodeBase64UrlSegment(parts[0]));
  if (!header) {
    return null;
  }

  const payload = parseJsonObject(decodeBase64UrlSegment(parts[1]));
  if (!payload) {
    return null;
  }

  return payload as JwtTokenClaims;
}

export function isJwtSessionTokenValid(token: unknown, nowMs: number = Date.now()): JwtValidationResult {
  if (typeof token !== 'string' || !token.trim()) {
    return { valid: false, reason: 'Missing authentication token' };
  }

  const payload = decodeJwtPayload(token);
  if (!payload) {
    return { valid: false, reason: 'Malformed JWT token' };
  }

  if (payload.tokenType === 'APPLICATION_TRACKING' || payload.scope === 'APPLICATION_DETAILS') {
    return { valid: false, reason: 'Application tracking token is not a dashboard session token' };
  }

  if (typeof payload.exp !== 'number' || !Number.isFinite(payload.exp)) {
    return { valid: false, reason: 'JWT token is missing a valid expiration claim' };
  }

  if (payload.exp * 1000 <= nowMs) {
    return { valid: false, reason: 'Authentication token has expired' };
  }

  if (typeof payload.nbf === 'number' && Number.isFinite(payload.nbf)) {
    if (payload.nbf * 1000 > nowMs + CLOCK_SKEW_TOLERANCE_MS) {
      return { valid: false, reason: 'Authentication token is not valid yet' };
    }
  }

  return { valid: true, payload };
}

export function validateStoredUserSession(raw: unknown, nowMs: number = Date.now()): StoredSessionValidationResult {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return { valid: false, user: null, reason: 'Stored user payload is not an object' };
  }

  const candidate = raw as Partial<User> & Record<string, unknown>;
  const tokenCheck = isJwtSessionTokenValid(candidate.token, nowMs);
  if (!tokenCheck.valid || !tokenCheck.payload) {
    return { valid: false, user: null, reason: tokenCheck.reason };
  }

  const claims = tokenCheck.payload;

  const rawRole = typeof candidate.role === 'string' ? candidate.role.trim() : '';
  if (!isValidUserRole(rawRole)) {
    return { valid: false, user: null, reason: 'Invalid or unsupported user role' };
  }

  if (claims.role !== undefined && claims.role !== null) {
    const tokenRole = String(claims.role).trim();
    if (!isValidUserRole(tokenRole) || tokenRole !== rawRole) {
      return { valid: false, user: null, reason: 'Stored role does not match token role claim' };
    }
  }

  const rawId = Number(candidate.id);
  if (!Number.isFinite(rawId) || rawId <= 0) {
    return { valid: false, user: null, reason: 'Missing or invalid user identifier' };
  }

  if (claims.userId !== undefined && claims.userId !== null) {
    const tokenUserId = Number(claims.userId);
    if (!Number.isFinite(tokenUserId) || tokenUserId !== rawId) {
      return { valid: false, user: null, reason: 'Stored user id does not match token claim' };
    }
  }

  const rawEmail = typeof candidate.email === 'string' ? candidate.email.trim().toLowerCase() : '';
  if (!rawEmail) {
    return { valid: false, user: null, reason: 'Missing user email' };
  }

  if (typeof claims.sub === 'string' && claims.sub.trim()) {
    if (claims.sub.trim().toLowerCase() !== rawEmail) {
      return { valid: false, user: null, reason: 'Stored email does not match token subject' };
    }
  }

  const normalizedStatus = typeof candidate.status === 'string' ? candidate.status.trim().toUpperCase() : '';
  if (normalizedStatus) {
    if (BLOCKED_ACCOUNT_STATUSES.has(normalizedStatus) || normalizedStatus !== 'ACTIVE') {
      return { valid: false, user: null, reason: `Account status "${normalizedStatus}" is not active` };
    }
  }

  const normalizedVerification = typeof candidate.verificationStatus === 'string'
    ? candidate.verificationStatus.trim().toUpperCase()
    : '';
  if (normalizedVerification === 'REJECTED' || normalizedVerification === 'UNDER_REVIEW') {
    return { valid: false, user: null, reason: `Account verification status "${normalizedVerification}" is not approved` };
  }

  if (rawRole === 'ROLE_BUS_OPERATOR' || rawRole === 'ROLE_HOTEL_MANAGER') {
    if (candidate.verified === false && normalizedVerification !== 'APPROVED') {
      return { valid: false, user: null, reason: 'Partner account is not verified/approved' };
    }
    if (normalizedVerification && normalizedVerification !== 'APPROVED' && candidate.verified !== true) {
      return { valid: false, user: null, reason: 'Partner verification status is not approved' };
    }
  }

  const tokenFullName = typeof claims.fullName === 'string' ? claims.fullName.trim() : '';
  const storedFullName = typeof candidate.fullName === 'string' ? candidate.fullName.trim() : '';

  const normalizedUser: User = {
    ...(candidate as User),
    id: rawId,
    email: rawEmail,
    fullName: storedFullName || tokenFullName || rawEmail,
    role: rawRole,
    token: String(candidate.token).trim()
  };

  return { valid: true, user: normalizedUser };
}

export function readAndValidateStoredSession(
  storage: Storage | null | undefined = typeof localStorage !== 'undefined' ? localStorage : null,
  nowMs: number = Date.now()
): User | null {
  if (!storage) {
    return null;
  }

  let rawJson: string | null = null;
  try {
    rawJson = storage.getItem(AUTH_USER_STORAGE_KEY);
  } catch {
    return null;
  }

  if (!rawJson || !rawJson.trim()) {
    return null;
  }

  try {
    const parsed: unknown = JSON.parse(rawJson);
    const result = validateStoredUserSession(parsed, nowMs);
    if (result.valid && result.user) {
      return result.user;
    }
  } catch {
    // Malformed JSON is cleared cleanly below
  }

  try {
    storage.removeItem(AUTH_USER_STORAGE_KEY);
  } catch {
    // Ignored when storage is unavailable
  }

  return null;
}

export function resolveDashboardRoute(
  user: Pick<User, 'role' | 'status' | 'verificationStatus'> | null | undefined
): string | null {
  if (!user || !isValidUserRole(user.role)) {
    return null;
  }

  const statusUpper = typeof user.status === 'string' ? user.status.trim().toUpperCase() : '';
  const verificationUpper = typeof user.verificationStatus === 'string'
    ? user.verificationStatus.trim().toUpperCase()
    : '';

  if (statusUpper === 'PENDING' || verificationUpper === 'UNDER_REVIEW') {
    return null;
  }
  if (statusUpper && statusUpper !== 'ACTIVE') {
    return null;
  }
  if (verificationUpper === 'REJECTED') {
    return null;
  }

  return ROLE_DASHBOARD_ROUTES[user.role];
}

export function canRoleAccessPath(role: Role, targetUrl: string): boolean {
  const path = targetUrl.split('?')[0].split('#')[0].replace(/\/+$/, '') || '/';

  if (path === '/' || path === '/home' || path === '/login' || path.startsWith('/register')) {
    return false;
  }
  if (path === '/admin' || path.startsWith('/admin/')) {
    return role === 'ROLE_ADMIN';
  }
  if (path === '/bus-operator' || path.startsWith('/bus-operator/')) {
    return role === 'ROLE_BUS_OPERATOR' || role === 'ROLE_ADMIN';
  }
  if (path === '/hotel-manager' || path.startsWith('/hotel-manager/')) {
    return role === 'ROLE_HOTEL_MANAGER' || role === 'ROLE_ADMIN';
  }
  if (path === '/customer' || path.startsWith('/customer/')) {
    return role === 'ROLE_CUSTOMER';
  }
  return false;
}

export function resolvePostLoginRoute(
  user: Pick<User, 'role' | 'status' | 'verificationStatus'> | null | undefined,
  returnUrl?: string | null
): string {
  if (!user) {
    return '/home';
  }

  const statusUpper = typeof user.status === 'string' ? user.status.trim().toUpperCase() : '';
  const verificationUpper = typeof user.verificationStatus === 'string'
    ? user.verificationStatus.trim().toUpperCase()
    : '';

  if (statusUpper === 'PENDING' || verificationUpper === 'UNDER_REVIEW') {
    return '/account-pending';
  }

  const trimmedReturnUrl = typeof returnUrl === 'string' ? returnUrl.trim() : '';
  if (
    trimmedReturnUrl.startsWith('/') &&
    !trimmedReturnUrl.startsWith('//') &&
    isValidUserRole(user.role) &&
    canRoleAccessPath(user.role, trimmedReturnUrl)
  ) {
    return trimmedReturnUrl;
  }

  return resolveDashboardRoute(user) ?? '/home';
}

export function resolveRevalidatedSessionUser(
  storedUser: User,
  details: BackendApplicationStatusSnapshot | null | undefined
): SessionRevalidationResult {
  if (!details || typeof details !== 'object') {
    return { valid: true, updatedUser: storedUser };
  }

  if (details.role !== undefined && details.role !== null) {
    const serverRole = String(details.role).trim();
    if (!isValidUserRole(serverRole) || serverRole !== storedUser.role) {
      return { valid: false, redirectTo: '/login', reason: 'User role no longer matches session' };
    }
  }

  const accountStatus = String(details.accountStatus ?? details.status ?? '').trim().toUpperCase();
  if (accountStatus === 'SUSPENDED') {
    return { valid: false, redirectTo: '/account-suspended', reason: 'Account is suspended' };
  }
  if (accountStatus === 'REJECTED') {
    return { valid: false, redirectTo: '/account-rejected', reason: 'Account is rejected' };
  }
  if (accountStatus && accountStatus !== 'ACTIVE') {
    return {
      valid: false,
      redirectTo: accountStatus === 'PENDING' ? '/account-pending' : '/login',
      reason: `Account status "${accountStatus}" is not active`
    };
  }

  const verificationStatus = String(details.verificationStatus ?? details.applicationStatus ?? '')
    .trim()
    .toUpperCase();

  if (verificationStatus === 'REJECTED') {
    return { valid: false, redirectTo: '/account-rejected', reason: 'Account verification was rejected' };
  }

  const isPartner = storedUser.role === 'ROLE_BUS_OPERATOR' || storedUser.role === 'ROLE_HOTEL_MANAGER';
  if (isPartner) {
    const isApproved = verificationStatus === 'APPROVED' || details.verified === true;
    if (!isApproved) {
      return { valid: false, redirectTo: '/login', reason: 'Partner account is no longer approved' };
    }
  }

  const updatedUser: User = {
    ...storedUser,
    fullName: (typeof details.fullName === 'string' && details.fullName.trim())
      ? details.fullName.trim()
      : storedUser.fullName,
    status: accountStatus || storedUser.status,
    verified: details.verified !== undefined ? Boolean(details.verified) : storedUser.verified,
    verificationStatus: isPartner
      ? (verificationStatus || storedUser.verificationStatus)
      : storedUser.verificationStatus
  };

  return { valid: true, updatedUser };
}
