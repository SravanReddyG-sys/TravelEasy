import { Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { LoginComponent } from './components/login/login.component';
import { RegisterSelectComponent } from './components/register/register-select.component';
import { TravellerRegisterComponent } from './components/register/traveller-register.component';
import { BusOperatorRegisterComponent } from './components/register/bus-operator-register.component';
import { HotelPartnerRegisterComponent } from './components/register/hotel-partner-register.component';
import { ForgotPasswordComponent } from './components/password/forgot-password.component';
import { ResetPasswordComponent } from './components/password/reset-password.component';
import { AccountPendingComponent } from './components/account-status/account-pending.component';
import { AccountRejectedComponent } from './components/account-status/account-rejected.component';
import { AccountSuspendedComponent } from './components/account-status/account-suspended.component';
import { ApplicationDetailsComponent } from './components/application-details/application-details.component';
import { CustomerDashboardComponent } from './components/customer-dashboard/customer-dashboard.component';
import { BusOperatorDashboardComponent } from './components/bus-operator-dashboard/bus-operator-dashboard.component';
import { HotelManagerDashboardComponent } from './components/hotel-manager-dashboard/hotel-manager-dashboard.component';
import { AdminDashboardComponent } from './components/admin-dashboard/admin-dashboard.component';
import { roleGuard } from './guards/role.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'home', pathMatch: 'full' },
  { path: 'home', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterSelectComponent },
  { path: 'register/traveller', component: TravellerRegisterComponent },
  { path: 'register/bus-operator', component: BusOperatorRegisterComponent },
  { path: 'register/hotel-partner', component: HotelPartnerRegisterComponent },
  { path: 'forgot-password', component: ForgotPasswordComponent },
  { path: 'reset-password', component: ResetPasswordComponent },
  { path: 'account-pending', component: AccountPendingComponent },
  { path: 'application-details', component: ApplicationDetailsComponent },
  { path: 'account-rejected', component: AccountRejectedComponent },
  { path: 'account-suspended', component: AccountSuspendedComponent },
  { path: 'customer', component: CustomerDashboardComponent },
  { path: 'customer/destinations', component: CustomerDashboardComponent },
  { path: 'customer/destinations/:slug', component: CustomerDashboardComponent },
  { path: 'customer/buses', component: CustomerDashboardComponent },
  { path: 'customer/hotels', component: CustomerDashboardComponent },
  { path: 'customer/bookings', component: CustomerDashboardComponent },
  { path: 'customer/favourites', component: CustomerDashboardComponent },
  { path: 'customer/profile', component: CustomerDashboardComponent },
  { path: 'customer/settings', component: CustomerDashboardComponent },
  { path: 'customer/support', component: CustomerDashboardComponent },
  {
    path: 'bus-operator',
    component: BusOperatorDashboardComponent,
    canActivate: [roleGuard],
    data: { roles: ['ROLE_BUS_OPERATOR', 'ROLE_ADMIN'] }
  },
  { path: 'hotel-manager', component: HotelManagerDashboardComponent },
  { path: 'admin', component: AdminDashboardComponent },
  { path: '**', redirectTo: 'home' }
];
