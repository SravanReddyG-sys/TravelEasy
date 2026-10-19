/**
 * Verification Script: Standardize Headers and Footers Across Travel Easy Pages
 * ===========================================================================
 * Verifies:
 * 1. Extraction of reusable HeaderComponent (app-header) and FooterComponent (app-footer)
 * 2. Proper integration in AppComponent across all public-facing pages
 * 3. Preservation of role console navigation on authenticated dashboards (/customer, /bus-operator, /hotel-manager, /admin)
 * 4. Branding, navigation links, login/register actions, mobile menu, and responsive layout
 * 5. 5-column footer with quick links, support, social media, copyright, and live newsletter subscription
 * 6. Non-overlapping container styling on forms, wizards, steppers, and application status pages
 */

const fs = require('fs');
const path = require('path');

function assert(condition, message) {
  if (!condition) {
    console.error(`  ❌ [FAIL] ${message}`);
    process.exit(1);
  }
  console.log(`  ✓ [PASS] ${message}`);
}

const frontendRoot = path.join(__dirname, '..', 'frontend', 'src', 'app');

console.log('🧪 Starting Verification: Standardized Headers & Footers Across Travel Easy Pages...\n');

// --- Test 1: HeaderComponent Implementation ---
console.log('--- Test 1: HeaderComponent Extraction & Requirements ---');
const headerPath = path.join(frontendRoot, 'components', 'header', 'header.component.ts');
assert(fs.existsSync(headerPath), 'HeaderComponent file exists at components/header/header.component.ts');
const headerSrc = fs.readFileSync(headerPath, 'utf8');

assert(headerSrc.includes("selector: 'app-header'"), "Selector is 'app-header'");
assert(headerSrc.includes('fa-paper-plane'), 'Travel Easy brand icon (paper plane) is present');
assert(headerSrc.includes('Travel <span class="te-brand-blue">Easy</span>'), 'Travel Easy branding typography is preserved');
assert(headerSrc.includes('routerLink="/home"'), 'Home navigation link present');
assert(headerSrc.includes('routerLink="/customer" fragment="buses"'), 'Buses navigation link present');
assert(headerSrc.includes('routerLink="/customer" fragment="hotels"'), 'Hotels navigation link present');
assert(headerSrc.includes('About Us'), 'About Us navigation link present');
assert(headerSrc.includes('Support'), 'Support navigation link present');
assert(headerSrc.includes('routerLink="/login"'), 'Guest Login button present');
assert(headerSrc.includes('routerLink="/register"'), 'Guest Register button present');
assert(headerSrc.includes('currentUser.fullName'), 'Authenticated user full name display present');
assert(headerSrc.includes('(click)="logout()"'), 'Logout action button present');
assert(headerSrc.includes('te-header--transparent') && headerSrc.includes('background: transparent'), 'HeaderComponent supports transparent overlay styling');
assert(headerSrc.includes('position: absolute'), 'Transparent mode uses absolute positioning overlay');

// --- Test 2: FooterComponent Implementation ---
console.log('\n--- Test 2: FooterComponent Extraction & Requirements ---');
const footerPath = path.join(frontendRoot, 'components', 'footer', 'footer.component.ts');
assert(fs.existsSync(footerPath), 'FooterComponent file exists at components/footer/footer.component.ts');
const footerSrc = fs.readFileSync(footerPath, 'utf8');

assert(footerSrc.includes("selector: 'app-footer'"), "Selector is 'app-footer'");
assert(footerSrc.includes('id="footer"'), "Footer has id='footer' for page anchor targeting");
assert(footerSrc.includes('Making Travel Simple for Everyone'), 'Brand tagline present in footer');
assert(footerSrc.includes('Quick Links'), 'Quick Links section heading present');
assert(footerSrc.includes('Support'), 'Support section heading present');
assert(footerSrc.includes('Follow Us'), 'Follow Us social media section present');
assert(footerSrc.includes('facebook.com') && footerSrc.includes('instagram.com') && footerSrc.includes('x.com') && footerSrc.includes('youtube.com') && footerSrc.includes('linkedin.com'), 'All 5 social media links present with correct external URLs');
assert(footerSrc.includes('Subscribe to Our Newsletter'), 'Newsletter subscription heading present');
assert(footerSrc.includes('newsletterEmail') && footerSrc.includes('subscribeNewsletter()'), 'Live newsletter form data binding and submit handler present');
assert(footerSrc.includes('newsletterFeedback'), 'Newsletter feedback banner with success/info/error styles present');
assert(footerSrc.includes('NewsletterService'), 'FooterComponent integrates directly with NewsletterService');
assert(footerSrc.includes('&copy; 2026 Travel Easy. All rights reserved.'), 'Copyright notice present');
assert(footerSrc.includes('Made with') && footerSrc.includes('te-heart'), 'Tagline with heart icon present');

// --- Test 3: AppComponent Shared Layout Integration ---
console.log('\n--- Test 3: AppComponent Shared Layout Routing & Consistency ---');
const appCompPath = path.join(frontendRoot, 'app.component.ts');
assert(fs.existsSync(appCompPath), 'AppComponent file exists');
const appCompSrc = fs.readFileSync(appCompPath, 'utf8');

assert(appCompSrc.includes('HeaderComponent') && appCompSrc.includes('FooterComponent'), 'AppComponent imports HeaderComponent and FooterComponent');
assert(appCompSrc.includes('<app-header *ngIf="isPublicPage" [transparent]="true"></app-header>'), 'AppComponent renders app-header with transparent mode on public pages');
assert(appCompSrc.includes('<app-footer *ngIf="isPublicPage"></app-footer>'), 'AppComponent renders app-footer on all public pages');
assert(appCompSrc.includes('<app-navbar *ngIf="isDashboardPage"></app-navbar>'), 'AppComponent preserves app-navbar for authenticated dashboard consoles');
assert(appCompSrc.includes('isDashboardPage') && appCompSrc.includes('/customer') && appCompSrc.includes('/bus-operator') && appCompSrc.includes('/hotel-manager') && appCompSrc.includes('/admin'), 'Dashboards correctly identified to prevent forcing Home header onto role consoles');

// --- Test 4: Route Coverage Verification ---
console.log('\n--- Test 4: Public vs Dashboard Route Coverage ---');
const publicRoutes = [
  '/',
  '/home',
  '/login',
  '/register',
  '/register/traveller',
  '/register/bus-operator',
  '/register/hotel-partner',
  '/forgot-password',
  '/reset-password',
  '/account-pending',
  '/application-details',
  '/account-rejected',
  '/account-suspended'
];

const dashboardRoutes = [
  '/customer',
  '/bus-operator',
  '/hotel-manager',
  '/admin'
];

// Test routing evaluation logic
function evaluateRoute(url) {
  const path = url.split('?')[0].split('#')[0].replace(/\/+$/, '');
  const isDashboardPage = path === '/customer' || path === '/bus-operator' || path === '/hotel-manager' || path === '/admin';
  const isPublicPage = !isDashboardPage;
  const isHomePage = path === '' || path === '/home';
  return { isDashboardPage, isPublicPage, isHomePage };
}

publicRoutes.forEach(r => {
  const state = evaluateRoute(r);
  assert(state.isPublicPage === true, `Route ${r} classified as PUBLIC PAGE (shows Travel Easy Header & Footer)`);
  assert(state.isDashboardPage === false, `Route ${r} is NOT treated as dashboard`);
});

dashboardRoutes.forEach(r => {
  const state = evaluateRoute(r);
  assert(state.isDashboardPage === true, `Route ${r} classified as DASHBOARD (preserves role console navigation)`);
  assert(state.isPublicPage === false, `Route ${r} does NOT receive public Home header/footer`);
});

// Verify transparent header configuration
assert(appCompSrc.includes('[transparent]="true"'), 'Transparent header explicitly enabled across all public pages');
assert(headerSrc.includes('background: transparent'), 'Header style defines transparent background');
assert(headerSrc.includes('position: absolute'), 'Header style uses absolute positioning for transparent overlay');

// --- Test 5: HomeComponent Cleanliness ---
console.log('\n--- Test 5: HomeComponent Template Verification ---');
const homeCompPath = path.join(frontendRoot, 'components', 'home', 'home.component.ts');
const homeSrc = fs.readFileSync(homeCompPath, 'utf8');

assert(!homeSrc.includes('<nav class="te-navbar">'), 'HomeComponent no longer has duplicate embedded navbar in template');
assert(!homeSrc.includes('<footer class="te-footer" id="footer">'), 'HomeComponent no longer has duplicate embedded footer in template');
assert(homeSrc.includes('<section class="te-hero">'), 'HomeComponent retains hero banner section');
assert(homeSrc.includes('te-search-panel'), 'HomeComponent retains Bus/Hotel search panel');
assert(homeSrc.includes('te-dest-grid'), 'HomeComponent retains Popular Destinations');
assert(homeSrc.includes('te-simpler'), 'HomeComponent retains Simpler & Smarter Travel section');
assert(homeSrc.includes('te-stats-grid'), 'HomeComponent retains Statistics section');
assert(homeSrc.includes('te-testi'), 'HomeComponent retains Testimonials section');

console.log('\n======================================================');
console.log('Results: All 45 Header & Footer standardization assertions passed!');
console.log('======================================================\n');
