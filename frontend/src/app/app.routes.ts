import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { CustomerDashboardComponent } from './components/customer-dashboard/customer-dashboard.component';
import { BusOperatorDashboardComponent } from './components/bus-operator-dashboard/bus-operator-dashboard.component';
import { HotelManagerDashboardComponent } from './components/hotel-manager-dashboard/hotel-manager-dashboard.component';
import { AdminDashboardComponent } from './components/admin-dashboard/admin-dashboard.component';

export const routes: Routes = [
  { path: '', redirectTo: 'customer', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'customer', component: CustomerDashboardComponent },
  { path: 'bus-operator', component: BusOperatorDashboardComponent },
  { path: 'hotel-manager', component: HotelManagerDashboardComponent },
  { path: 'admin', component: AdminDashboardComponent },
  { path: '**', redirectTo: 'customer' }
];
