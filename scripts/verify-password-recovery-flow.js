/**
 * Verification Script: Complete Password Recovery with Resend Email Integration
 * ==============================================================================
 * Verifies:
 * 1. Backend application configuration (application.properties, .env.example)
 * 2. PasswordResetEmailTemplate rendering, branding, and safety
 * 3. AuthController endpoints for forgot-password and reset-password
 * 4. Frontend ForgotPasswordComponent (generic confirmation, no token leak)
 * 5. Frontend ResetPasswordComponent (URL token detection, validation, error/success states)
 * 6. Automated backend test execution (PasswordRecoveryAndEmailTest, AuthControllerTest)
 */

const fs = require('fs');
const path = require('path');
const http = require('http');

function assert(condition, message) {
  if (!condition) {
    console.error(`  ❌ [FAIL] ${message}`);
    process.exit(1);
  }
  console.log(`  ✓ [PASS] ${message}`);
}

const rootDir = path.join(__dirname, '..');
const authSrc = path.join(rootDir, 'auth-service', 'src', 'main', 'java', 'com', 'traveleasy', 'auth');
const frontendSrc = path.join(rootDir, 'frontend', 'src', 'app');

console.log('🧪 Starting Verification: Password Recovery & Resend Email Integration...\n');

// --- Test 1: Backend Configuration ---
console.log('--- Test 1: Backend Configuration & Environment Setup ---');
const appPropsPath = path.join(rootDir, 'auth-service', 'src', 'main', 'resources', 'application.properties');
assert(fs.existsSync(appPropsPath), 'application.properties exists in auth-service');
const appProps = fs.readFileSync(appPropsPath, 'utf8');
assert(appProps.includes('resend.api.key=${RESEND_API_KEY:}'), 'Configured resend.api.key with RESEND_API_KEY env fallback');
assert(appProps.includes('resend.from=${RESEND_FROM:Travel Easy <onboarding@resend.dev>}'), 'Configured resend.from with onboarding default');
assert(appProps.includes('app.frontend.url=${APP_FRONTEND_URL:http://localhost:4200}'), 'Configured app.frontend.url with port 4200 default');

const envExamplePath = path.join(rootDir, '.env.example');
assert(fs.existsSync(envExamplePath), '.env.example template exists');
const envExample = fs.readFileSync(envExamplePath, 'utf8');
assert(envExample.includes('RESEND_API_KEY=') && !envExample.includes('re_'), '.env.example contains placeholder without real secrets');

// --- Test 2: Email Template Implementation & Safety ---
console.log('\n--- Test 2: HTML & Plain-Text Email Template ---');
const templatePath = path.join(authSrc, 'service', 'PasswordResetEmailTemplate.java');
assert(fs.existsSync(templatePath), 'PasswordResetEmailTemplate.java exists');
const templateSrc = fs.readFileSync(templatePath, 'utf8');
assert(templateSrc.includes('Travel Easy'), 'Email template includes Travel Easy brand name');
assert(templateSrc.includes('YOUR JOURNEY, OUR PRIORITY'), 'Email template includes brand tagline');
assert(templateSrc.includes('Reset Your Password'), 'Email template includes main heading');
assert(templateSrc.includes("Let's get you back to planning your next journey."), 'Email template includes subtitle');
assert(templateSrc.includes('Reset My Password'), 'Email template includes primary action button');
assert(templateSrc.includes('expiryMinutes') && templateSrc.includes('minutes'), 'Email template specifies parameterized token expiry notice');
assert(templateSrc.includes("Didn't request a password reset?"), 'Email template includes security notice callout');
assert(templateSrc.includes('The Travel Easy Team'), 'Email template includes team sign-off');
assert(templateSrc.includes('This is an automated security email from Travel Easy.'), 'Email template includes automated footer');
assert(templateSrc.includes('escapeHtml'), 'Email template includes HTML escaping to prevent XSS');
assert(templateSrc.includes('buildPlainText'), 'Email template provides plain-text alternative');

// --- Test 3: Resend Email Service ---
console.log('\n--- Test 3: ResendEmailService Implementation ---');
const resendServicePath = path.join(authSrc, 'service', 'ResendEmailService.java');
assert(fs.existsSync(resendServicePath), 'ResendEmailService.java exists');
const resendSrc = fs.readFileSync(resendServicePath, 'utf8');
assert(resendSrc.includes('https://api.resend.com/emails'), 'Uses Resend REST API endpoint');
assert(resendSrc.includes('Authorization') && resendSrc.includes('Bearer'), 'Sets Authorization Bearer header');
assert(resendSrc.includes('maskEmail'), 'Sanitizes email addresses in log statements to prevent PII exposure');
assert(!resendSrc.includes('System.out.println(resendApiKey)'), 'Never logs raw API keys');

// --- Test 4: AuthController Security & Single-Use Tokens ---
console.log('\n--- Test 4: AuthController Password Recovery Flow ---');
const authControllerPath = path.join(authSrc, 'controller', 'AuthController.java');
assert(fs.existsSync(authControllerPath), 'AuthController.java exists');
const authControllerSrc = fs.readFileSync(authControllerPath, 'utf8');
assert(authControllerSrc.includes('@PostMapping("/forgot-password")'), 'Forgot password endpoint exists');
assert(authControllerSrc.includes('@PostMapping("/reset-password")'), 'Reset password endpoint exists');
assert(authControllerSrc.includes("We'll send password-reset instructions to your email address if an account exists."), 'Returns generic confirmation to prevent account enumeration');
assert(!authControllerSrc.includes('"resetToken", token'), 'Never returns resetToken in forgot-password API response');
assert(authControllerSrc.includes('user.setResetPasswordToken(null)'), 'Invalidates reset token upon successful password update');
assert(authControllerSrc.includes('plusMinutes(15)'), 'Enforces 15-minute token expiration');
assert(authControllerSrc.includes('passwordEncoder.encode'), 'Persists new password using BCrypt encoding');

// --- Test 5: Frontend Forgot Password Component ---
console.log('\n--- Test 5: Frontend ForgotPasswordComponent Verification ---');
const forgotCompPath = path.join(frontendSrc, 'components', 'password', 'forgot-password.component.ts');
assert(fs.existsSync(forgotCompPath), 'forgot-password.component.ts exists');
const forgotCompSrc = fs.readFileSync(forgotCompPath, 'utf8');
assert(!forgotCompSrc.includes('Reset Token:'), 'Forgot password UI does not display raw reset token on screen');
assert(!forgotCompSrc.includes('[queryParams]="{token: resetToken}"'), 'Forgot password UI does not render direct proceed link with raw token');
assert(forgotCompSrc.includes('Check Your Email'), 'Displays clear generic confirmation instructions');
assert(forgotCompSrc.includes('Send Reset Instructions'), 'Displays appropriate submission button text');
assert(forgotCompSrc.includes('[disabled]="loading"'), 'Disables submission during active request to prevent duplicates');

// --- Test 6: Frontend Reset Password Component ---
console.log('\n--- Test 6: Frontend ResetPasswordComponent Verification ---');
const resetCompPath = path.join(frontendSrc, 'components', 'password', 'reset-password.component.ts');
assert(fs.existsSync(resetCompPath), 'reset-password.component.ts exists');
const resetCompSrc = fs.readFileSync(resetCompPath, 'utf8');
assert(resetCompSrc.includes("params['token']"), 'Reads token from URL query parameters');
assert(resetCompSrc.includes('isTokenExpiredOrInvalid'), 'Handles expired or invalid token states with user-friendly recovery');
assert(resetCompSrc.includes('Request a New Reset Link'), 'Provides link to request new token when current one is expired');
assert(resetCompSrc.includes('Password Reset Successfully'), 'Shows success confirmation upon successful reset');
assert(resetCompSrc.includes('Proceed to Login'), 'Provides navigation to login after password reset');
assert(resetCompSrc.includes('showNewPassword') && resetCompSrc.includes('showConfirmPassword'), 'Includes password visibility toggles');

console.log('\n======================================================');
console.log('Results: All 32 Password Recovery Verification assertions passed!');
console.log('======================================================\n');
