import { Routes } from '@angular/router';
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
import { CustomerDashboardComponent } from './components/customer-dashboard/customer-dashboard.component';
import { BusOperatorDashboardComponent } from './components/bus-operator-dashboard/bus-operator-dashboard.component';
import { HotelManagerDashboardComponent } from './components/hotel-manager-dashboard/hotel-manager-dashboard.component';
import { AdminDashboardComponent } from './components/admin-dashboard/admin-dashboard.component';

export const routes: Routes = [
  { path: '', redirectTo: 'customer', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterSelectComponent },
  { path: 'register/traveller', component: TravellerRegisterComponent },
  { path: 'register/bus-operator', component: BusOperatorRegisterComponent },
  { path: 'register/hotel-partner', component: HotelPartnerRegisterComponent },
  { path: 'forgot-password', component: ForgotPasswordComponent },
  { path: 'reset-password', component: ResetPasswordComponent },
  { path: 'account-pending', component: AccountPendingComponent },
  { path: 'account-rejected', component: AccountRejectedComponent },
  { path: 'account-suspended', component: AccountSuspendedComponent },
  { path: 'customer', component: CustomerDashboardComponent },
  { path: 'bus-operator', component: BusOperatorDashboardComponent },
  { path: 'hotel-manager', component: HotelManagerDashboardComponent },
  { path: 'admin', component: AdminDashboardComponent },
  { path: '**', redirectTo: 'customer' }
];
