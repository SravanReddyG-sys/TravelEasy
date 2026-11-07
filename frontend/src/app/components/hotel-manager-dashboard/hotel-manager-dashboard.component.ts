import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../../services/auth.service';
import { HotelService } from '../../services/hotel.service';
import { Hotel, HotelRoom, HotelImage, HotelStep1Request, HotelStep1Response, HotelStep2Request, HotelStep2Response, HotelStep3Request, HotelStep3Response, HotelStep4Request, HotelStep4Response, HotelStep5SubmitRequest, HotelStep5Response } from '../../models/hotel.model';
import { BookingService, HotelRevenueReportResponse } from '../../services/booking.service';
import { NotificationService } from '../../services/notification.service';
import { User } from '../../models/user.model';
import { FormattedNotification } from '../../models/notification.model';

export interface DateRangePresetItem {
  id: string;
  label: string;
}

export interface KpiComparison {
  currentValue: number;
  previousValue: number;
  diff: number;
  percentageChange: number | null;
  isPositive: boolean;
  isNegative: boolean;
  isNeutral: boolean;
  trendText: string;
  hasData: boolean;
}

export interface TrendDayItem {
  dateLabel: string;
  fullDate: string;
  count: number;
  revenue: number;
  heightPx: number;
  revenueHeightPx: number;
  heightPercent: number;
  revenueHeightPercent: number;
  showLabel: boolean;
}

@Component({
  selector: 'app-hotel-manager-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './hotel-manager-dashboard.component.html',
  styleUrls: ['./hotel-manager-dashboard.component.css']
})
export class HotelManagerDashboardComponent implements OnInit, OnDestroy {
  currentUser: User | null = null;
  activeNav: string = 'dashboard';
  isSidebarCollapsed: boolean = false;
  isMobileSidebarOpen: boolean = false;

  // ==================== PROPERTY REGISTRATION WIZARD STATE (WP-05 STEP 1) ====================
  propertyViewMode: 'list' | 'wizard' = 'list';
  currentWizardStep: number = 1;
  managerDrafts: any[] = [];

  step1Data = {
    id: null as number | null,
    name: '',
    propertyType: '',
    propertyClassification: '',
    customClassification: '',
    propertySegment: '',
    brandName: '',
    description: ''
  };

  wizardImages: (HotelImage | null)[] = [null, null, null, null];
  imageUploadErrors: string[] = ['', '', '', ''];
  imageUploading: boolean[] = [false, false, false, false];

  step1Submitted: boolean = false;
  step1Submitting: boolean = false;
  step1ErrorMessage: string | null = null;
  step1SuccessMessage: string | null = null;
  isStep1Dirty: boolean = false;
  showCancelWizardModal: boolean = false;

  // Step 2 State (Location & Address)
  step2Data = {
    addressLine1: '',
    addressLine2: '',
    country: 'India',
    state: '',
    city: '',
    postalCode: '',
    landmark: ''
  };

  step2Submitted: boolean = false;
  step2Submitting: boolean = false;
  step2ErrorMessage: string | null = null;
  isStep2Dirty: boolean = false;

  // Step 3 State (Property Information)
  step3Data = {
    // 1. Contact
    propertyContactNumber: '',
    alternateContactNumber: '',
    propertyEmail: '',
    websiteUrl: '',
    // 2. Business Registration
    registeredBusinessName: '',
    businessRegistrationType: 'Private Limited Company',
    otherBusinessRegistrationType: '',
    businessRegistrationNumber: '',
    registeredBusinessAddress: '',
    // 3. GST Details
    isGstRegistered: false,
    gstin: '',
    gstLegalName: '',
    gstState: '',
    // 4. Property Manager Details
    propertyManagerName: '',
    propertyManagerDesignation: 'General Manager',
    propertyManagerContactNumber: '',
    propertyManagerEmail: '',
    // 5. Operating Hours
    checkInTime: '14:00',
    checkOutTime: '11:00',
    receptionAvailability: '24-hour Reception',
    receptionOpeningTime: '',
    receptionClosingTime: ''
  };

  step3Submitted: boolean = false;
  step3Submitting: boolean = false;
  step3ErrorMessage: string | null = null;
  isStep3Dirty: boolean = false;

  // Accordion active subsection ('contact', 'business', 'gst', 'manager', 'operating')
  expandedStep3Section: string = 'contact';

  // ==================== STEP 4: FACILITIES & POLICIES ====================
  step4Data = {
    predefinedAmenities: [] as string[],
    customAmenities: [] as string[],
    childrenPolicy: 'Children Allowed',
    petPolicy: 'Pets Not Allowed',
    smokingPolicy: 'Smoking Prohibited',
    extraBedPolicy: 'Extra Bed Available',
    acceptedIdentificationTypes: ['Passport', 'Aadhaar Card'] as string[],
    customIdentificationType: '',
    additionalRules: ''
  };

  expandedStep4Section: 'amenities' | 'policies' | 'rules' | null = 'amenities';
  step4Submitted: boolean = false;
  step4Submitting: boolean = false;
  step4ErrorMessage: string | null = null;
  isStep4Dirty: boolean = false;

  showCustomAmenityInput: boolean = false;
  newCustomAmenityName: string = '';
  customAmenityError: string = '';

  predefinedAmenityCategories = [
    {
      category: 'Connectivity',
      icon: 'fa-solid fa-wifi',
      amenities: ['Wi-Fi', 'Business Centre'],
      fullWidth: false
    },
    {
      category: 'Dining',
      icon: 'fa-solid fa-utensils',
      amenities: ['Restaurant', 'Room Service', 'Complimentary Breakfast'],
      fullWidth: false
    },
    {
      category: 'Transport',
      icon: 'fa-solid fa-car',
      amenities: ['Parking', 'Airport Shuttle'],
      fullWidth: false
    },
    {
      category: 'Recreation',
      icon: 'fa-solid fa-water',
      amenities: ['Swimming Pool', 'Gym', 'Spa'],
      fullWidth: false
    },
    {
      category: 'Safety',
      icon: 'fa-solid fa-shield-halved',
      amenities: ['CCTV', '24-hour Security', 'Fire Safety'],
      fullWidth: false
    },
    {
      category: 'Accessibility',
      icon: 'fa-solid fa-wheelchair',
      amenities: ['Accessible Entrance', 'Elevator'],
      fullWidth: false
    },
    {
      category: 'General',
      icon: 'fa-solid fa-layer-group',
      amenities: ['Laundry', 'Reception', 'Luggage Storage'],
      fullWidth: true
    }
  ];

  childrenPolicyOptions: string[] = ['Children Allowed', 'Children Not Allowed'];
  petPolicyOptions: string[] = ['Pets Allowed', 'Pets Not Allowed'];
  smokingPolicyOptions: string[] = ['Smoking Prohibited', 'Designated Smoking Areas', 'Smoking Allowed'];
  extraBedPolicyOptions: string[] = ['Extra Bed Available', 'Extra Bed Not Available'];
  identificationTypeOptions: string[] = [
    'Passport',
    'Driving Licence',
    'Voter ID',
    'Government-issued Photo ID',
    'Aadhaar Card',
    'Other'
  ];

  // ==================== WP-05 STEP 5: REVIEW & SUBMIT STATE ====================
  reviewSectionsExpanded = {
    basic: true,
    address: false,
    propertyInfo: false,
    facilities: false
  };

  editingFromReview: boolean = false;
  accuracyConfirmed: boolean = false;
  step5Submitted: boolean = false;
  step5Submitting: boolean = false;
  step5ErrorMessage: string | null = null;
  registrationSuccess: boolean = false;
  activatedPropertyDetails: HotelStep5Response | null = null;
  showTermsModal: boolean = false;


  businessRegistrationTypes: string[] = [
    'Private Limited Company',
    'Public Limited Company',
    'Partnership',
    'Limited Liability Partnership (LLP)',
    'Sole Proprietorship',
    'Other'
  ];

  managerDesignations: string[] = [
    'General Manager',
    'Property Manager',
    'Operations Manager',
    'Hotel Administrator',
    'Owner / Proprietor',
    'Other'
  ];

  receptionAvailabilityOptions: string[] = [
    '24-hour Reception',
    'Limited Operating Hours'
  ];

  countries: string[] = [
    'India',
    'United States',
    'United Kingdom',
    'United Arab Emirates',
    'Australia',
    'Canada',
    'Singapore',
    'Germany',
    'France',
    'Japan',
    'Malaysia',
    'Thailand',
    'Saudi Arabia',
    'South Africa',
    'Italy',
    'Spain',
    'Netherlands',
    'Switzerland',
    'New Zealand'
  ];

  availableStates: string[] = [];

  countryStatesMap: { [country: string]: string[] } = {
    'India': [
      'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chhattisgarh',
      'Goa', 'Gujarat', 'Haryana', 'Himachal Pradesh', 'Jharkhand',
      'Karnataka', 'Kerala', 'Madhya Pradesh', 'Maharashtra', 'Manipur',
      'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha', 'Punjab',
      'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura',
      'Uttar Pradesh', 'Uttarakhand', 'West Bengal',
      'Andaman and Nicobar Islands', 'Chandigarh',
      'Dadra and Nagar Haveli and Daman and Diu', 'Delhi',
      'Jammu and Kashmir', 'Ladakh', 'Lakshadweep', 'Puducherry'
    ],
    'United States': [
      'Alabama', 'Alaska', 'Arizona', 'Arkansas', 'California', 'Colorado', 'Connecticut',
      'Delaware', 'District of Columbia', 'Florida', 'Georgia', 'Hawaii', 'Idaho', 'Illinois',
      'Indiana', 'Iowa', 'Kansas', 'Kentucky', 'Louisiana', 'Maine', 'Maryland', 'Massachusetts',
      'Michigan', 'Minnesota', 'Mississippi', 'Missouri', 'Montana', 'Nebraska', 'Nevada',
      'New Hampshire', 'New Jersey', 'New Mexico', 'New York', 'North Carolina', 'North Dakota',
      'Ohio', 'Oklahoma', 'Oregon', 'Pennsylvania', 'Rhode Island', 'South Carolina',
      'South Dakota', 'Tennessee', 'Texas', 'Utah', 'Vermont', 'Virginia', 'Washington',
      'West Virginia', 'Wisconsin', 'Wyoming'
    ],
    'United Kingdom': [
      'England', 'Scotland', 'Wales', 'Northern Ireland'
    ],
    'United Arab Emirates': [
      'Abu Dhabi', 'Dubai', 'Sharjah', 'Ajman', 'Umm Al Quwain', 'Ras Al Khaimah', 'Fujairah'
    ],
    'Australia': [
      'New South Wales', 'Victoria', 'Queensland', 'Western Australia',
      'South Australia', 'Tasmania', 'Australian Capital Territory', 'Northern Territory'
    ],
    'Canada': [
      'Ontario', 'Quebec', 'British Columbia', 'Alberta', 'Manitoba', 'Saskatchewan',
      'Nova Scotia', 'New Brunswick', 'Newfoundland and Labrador', 'Prince Edward Island',
      'Northwest Territories', 'Yukon', 'Nunavut'
    ],
    'Singapore': [
      'Central Region', 'East Region', 'North Region', 'North-East Region', 'West Region'
    ]
  };

  // Data State
  myHotels: any[] = [];
  selectedHotel: any = null;
  roomTypes: any[] = [];
  roomBlocks: any[] = [];
  managerReservations: any[] = [];
  filteredReservations: any[] = [];

  isLoading: boolean = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  // ==================== REPORTING DATE-RANGE FILTER STATE ====================
  showDateRangeDropdown: boolean = false;
  activePreset: string = 'last7';
  startDate: string = '';
  endDate: string = '';
  prevStartDate: string = '';
  prevEndDate: string = '';
  durationDays: number = 7;
  selectedDateRangeLabel: string = 'Last 7 Days';
  prevDateRangeLabel: string = '';

  isCustomRangeActive: boolean = false;
  customStartDate: string = '';
  customEndDate: string = '';
  dateRangeValidationError: string | null = null;

  datePresets: DateRangePresetItem[] = [
    { id: 'today', label: 'Today' },
    { id: 'yesterday', label: 'Yesterday' },
    { id: 'last7', label: 'Last 7 Days' },
    { id: 'last30', label: 'Last 30 Days' },
    { id: 'thisMonth', label: 'This Month' },
    { id: 'lastMonth', label: 'Last Month' },
    { id: 'custom', label: 'Custom Range...' }
  ];

  // Header Dropdown States
  showNotifications: boolean = false;
  showUserDropdown: boolean = false;
  showPropertySelectorDropdown: boolean = false;
  notificationList: FormattedNotification[] = [];
  unreadNotificationsCount: number = 0;

  // ==================== DATE-AWARE KPI METRIC COMPARISONS ====================
  bookingsKpi: KpiComparison = this.defaultKpiComparison();
  nightsKpi: KpiComparison = this.defaultKpiComparison();
  occupancyKpi: KpiComparison = this.defaultKpiComparison();
  revenueKpi: KpiComparison = this.defaultKpiComparison();

  // Occupancy Overview Metrics (Date-Wise WP-03 Architecture)
  occupancyRate: number = 0;
  occupiedRoomNights: number = 0;
  heldRoomNights: number = 0;
  availableRoomNights: number = 0;
  totalPhysicalRoomNights: number = 0;
  blockedRoomNights: number = 0;
  applicableRoomNightCapacity: number = 0;
  donutOffset: number = 251.2;

  // Bookings Trend Chart (Dynamic for Reporting Period)
  bookingsTrendToggle: 'bookings' | 'revenue' = 'bookings';
  trendDays: TrendDayItem[] = [];
  maxChartValue: number = 40;
  maxRevenueChartValue: number = 50000;

  // Corrected Operational Alerts
  operationalAlerts = {
    arrivalsCount: 0,
    departuresCount: 0,
    activeRoomBlocksCount: 0,
    lowAvailabilityRoomsCount: 0
  };

  // Upcoming / Reporting Period Reservations
  upcomingReservations: any[] = [];

  // Modals State
  showAddHotelModal: boolean = false;
  showAddRoomModal: boolean = false;
  showBlockRoomModal: boolean = false;
  showPriceOverrideModal: boolean = false;
  showReservationDetailsModal: boolean = false;
  selectedReservation: any = null;

  // Form Models - Add Hotel
  hotelName: string = '';
  hotelCity: string = 'Bengaluru';
  hotelAddress: string = '';
  hotelStarRating: number = 4;
  hotelDescription: string = '';
  hotelAmenities: string = 'Swimming Pool, Free Wi-Fi, Gym, Spa, Multi-Cuisine Restaurant';
  hotelImageUrl: string = 'https://images.unsplash.com/photo-1566073771259-6a8506099945';

  // Form Models - Add Room
  selectedHotelForRoom: any = null;
  roomTypeName: string = 'Deluxe Room';
  roomMaxOccupancy: number = 2;
  roomPricePerNight: number = 4500;
  roomTotalCount: number = 20;
  roomAmenities: string = 'King Bed, Air Conditioning, City View, Free High-Speed Wi-Fi, Breakfast Included';

  // Form Models - Block Rooms
  blockRoomId: number | null = null;
  blockStartDate: string = '';
  blockEndDate: string = '';
  blockCount: number = 2;
  blockReason: string = 'MAINTENANCE';
  blockNotes: string = '';

  // Form Models - Price Override
  overrideRoomId: number | null = null;
  overrideStartDate: string = '';
  overrideEndDate: string = '';
  overridePrice: number = 5500;

  // Subscriptions
  private subscriptions: Subscription = new Subscription();

  constructor(
    private authService: AuthService,
    private hotelService: HotelService,
    private bookingService: BookingService,
    private notificationService: NotificationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.currentUserValue;
    if (!this.currentUser) {
      this.router.navigate(['/login']);
      return;
    }

    this.initNotifications();
    this.initDefaultReportingPeriod();
    this.updateAvailableStates();
    this.loadInitialData();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.date-range-dropdown-container')) {
      this.showDateRangeDropdown = false;
    }
    if (!target.closest('.notifications-dropdown-container')) {
      this.showNotifications = false;
    }
    if (!target.closest('.user-dropdown-container')) {
      this.showUserDropdown = false;
    }
    if (!target.closest('.property-dropdown-container')) {
      this.showPropertySelectorDropdown = false;
    }
  }

  // ==================== REPORTING DATE RANGE INITIALIZATION & PRESETS ====================
  private initDefaultReportingPeriod(): void {
    this.selectPreset('last7', false);
  }

  selectPreset(presetId: string, triggerRefresh: boolean = true): void {
    const today = new Date();
    const todayIso = this.formatDateIso(today);
    let start = todayIso;
    let end = todayIso;
    let label = 'Today';

    switch (presetId) {
      case 'today': {
        start = todayIso;
        end = todayIso;
        label = `Today (${this.formatDateShort(start)})`;
        this.isCustomRangeActive = false;
        break;
      }
      case 'yesterday': {
        const y = new Date();
        y.setDate(today.getDate() - 1);
        start = this.formatDateIso(y);
        end = start;
        label = `Yesterday (${this.formatDateShort(start)})`;
        this.isCustomRangeActive = false;
        break;
      }
      case 'last7': {
        const d = new Date();
        d.setDate(today.getDate() - 6);
        start = this.formatDateIso(d);
        end = todayIso;
        label = `Last 7 Days (${this.formatDateShort(start)} – ${this.formatDateShort(end)})`;
        this.isCustomRangeActive = false;
        break;
      }
      case 'last30': {
        const d = new Date();
        d.setDate(today.getDate() - 29);
        start = this.formatDateIso(d);
        end = todayIso;
        label = `Last 30 Days (${this.formatDateShort(start)} – ${this.formatDateShort(end)})`;
        this.isCustomRangeActive = false;
        break;
      }
      case 'thisMonth': {
        const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);
        const lastDay = new Date(today.getFullYear(), today.getMonth() + 1, 0);
        start = this.formatDateIso(firstDay);
        end = this.formatDateIso(lastDay);
        label = `This Month (${this.formatDateShort(start)} – ${this.formatDateShort(end)})`;
        this.isCustomRangeActive = false;
        break;
      }
      case 'lastMonth': {
        const firstDayLastMonth = new Date(today.getFullYear(), today.getMonth() - 1, 1);
        const lastDayLastMonth = new Date(today.getFullYear(), today.getMonth(), 0);
        start = this.formatDateIso(firstDayLastMonth);
        end = this.formatDateIso(lastDayLastMonth);
        label = `Last Month (${this.formatDateShort(start)} – ${this.formatDateShort(end)})`;
        this.isCustomRangeActive = false;
        break;
      }
      case 'custom': {
        this.isCustomRangeActive = true;
        this.customStartDate = this.startDate;
        this.customEndDate = this.endDate;
        return;
      }
    }

    this.activePreset = presetId;
    this.setReportingRange(start, end, label);

    if (triggerRefresh) {
      this.showDateRangeDropdown = false;
      this.loadDashboardData();
    }
  }

  applyCustomDateRange(): void {
    if (!this.customStartDate || !this.customEndDate) {
      this.dateRangeValidationError = 'Please select both start and end dates.';
      return;
    }
    if (this.customStartDate > this.customEndDate) {
      this.dateRangeValidationError = 'Start date cannot exceed end date.';
      return;
    }

    this.dateRangeValidationError = null;
    this.activePreset = 'custom';
    const label = `Custom (${this.formatDateShort(this.customStartDate)} – ${this.formatDateShort(this.customEndDate)})`;
    this.setReportingRange(this.customStartDate, this.customEndDate, label);
    this.showDateRangeDropdown = false;
    this.loadDashboardData();
  }

  private setReportingRange(start: string, end: string, label: string): void {
    this.startDate = start;
    this.endDate = end;
    this.selectedDateRangeLabel = label;

    // Previous equivalent period calculation
    const prev = this.calculateEquivalentPreviousPeriod(start, end);
    this.prevStartDate = prev.prevStartDate;
    this.prevEndDate = prev.prevEndDate;
    this.durationDays = prev.durationDays;
    if (prev.prevStartDate === prev.prevEndDate) {
      this.prevDateRangeLabel = `${this.formatDateShort(prev.prevStartDate)} (1d)`;
    } else {
      this.prevDateRangeLabel = `${this.formatDateShort(prev.prevStartDate)} – ${this.formatDateShort(prev.prevEndDate)} (${prev.durationDays}d)`;
    }
  }

  calculateEquivalentPreviousPeriod(start: string, end: string): { prevStartDate: string; prevEndDate: string; durationDays: number } {
    const dStart = new Date(start + 'T00:00:00');
    const dEnd = new Date(end + 'T00:00:00');
    const diffTime = dEnd.getTime() - dStart.getTime();
    const durationDays = Math.round(diffTime / (1000 * 60 * 60 * 24)) + 1;

    // Previous period ends immediately before current start date
    const pEnd = new Date(dStart.getTime() - (24 * 60 * 60 * 1000));
    const pStart = new Date(pEnd.getTime() - ((durationDays - 1) * 24 * 60 * 60 * 1000));

    return {
      prevStartDate: this.formatDateIso(pStart),
      prevEndDate: this.formatDateIso(pEnd),
      durationDays
    };
  }

  toggleDateRangeDropdown(event: Event): void {
    event.stopPropagation();
    this.showDateRangeDropdown = !this.showDateRangeDropdown;
    this.showNotifications = false;
    this.showUserDropdown = false;
    this.showPropertySelectorDropdown = false;
  }

  // ==================== DATA LOADING (SHARED FILTER STATE) ====================
  loadInitialData(): void {
    if (!this.currentUser) return;
    this.isLoading = true;
    this.errorMessage = null;

    this.loadManagerDrafts();
    this.hotelService.getHotelsByManager(this.currentUser.id).subscribe({
      next: (hotels) => {
        this.myHotels = hotels || [];
        if (this.myHotels.length > 0) {
          if (!this.selectedHotel || !this.myHotels.some(h => h.id === this.selectedHotel.id)) {
            this.selectedHotel = this.myHotels[0];
          }
        } else {
          this.selectedHotel = null;
        }
        this.loadDashboardData();
      },
      error: (err) => {
        console.error('Failed to load manager hotels:', err);
        this.errorMessage = 'Failed to load hotel properties. Please refresh or try again.';
        this.isLoading = false;
      }
    });
  }

  selectHotel(hotel: any): void {
    this.selectedHotel = hotel;
    this.showPropertySelectorDropdown = false;
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    if (!this.currentUser) return;
    this.isLoading = true;

    if (!this.selectedHotel) {
      this.roomTypes = [];
      this.roomBlocks = [];
      this.fetchReservationsAndCompute(null, null);
      return;
    }

    const hotelId = this.selectedHotel.id;

    // Load Room Types & Room Blocks in parallel with inventory summary
    forkJoin({
      roomTypes: this.hotelService.getRoomTypes(hotelId).pipe(catchError(() => of([]))),
      roomBlocks: this.hotelService.getRoomBlocks(hotelId).pipe(catchError(() => of([]))),
      inventorySummary: this.hotelService.getHotelInventorySummary(hotelId, this.startDate, this.endDate).pipe(catchError(() => of(null))),
      prevInventorySummary: this.hotelService.getHotelInventorySummary(hotelId, this.prevStartDate, this.prevEndDate).pipe(catchError(() => of(null)))
    }).subscribe({
      next: (res) => {
        this.roomTypes = res.roomTypes || [];
        this.roomBlocks = res.roomBlocks || [];
        this.fetchReservationsAndCompute(res.inventorySummary, res.prevInventorySummary);
      },
      error: (err) => {
        console.warn('Dashboard data fetch error:', err);
        this.fetchReservationsAndCompute(null, null);
      }
    });
  }

  private fetchReservationsAndCompute(currInventorySummary: any, prevInventorySummary: any): void {
    this.bookingService.getProviderBookings(this.currentUser!.id).subscribe({
      next: (reservations) => {
        this.managerReservations = reservations || [];
        this.filterAndFetchRevenue(currInventorySummary, prevInventorySummary);
      },
      error: (err) => {
        console.error('Failed to load reservations:', err);
        this.managerReservations = [];
        this.filterAndFetchRevenue(currInventorySummary, prevInventorySummary);
      }
    });
  }

  private filterAndFetchRevenue(currInventorySummary: any, prevInventorySummary: any): void {
    if (this.selectedHotel) {
      this.filteredReservations = this.managerReservations.filter(res =>
        res.serviceId === this.selectedHotel.id ||
        (res.serviceTitle && res.serviceTitle.toLowerCase().includes(this.selectedHotel.name?.toLowerCase()))
      );
    } else {
      this.filteredReservations = [...this.managerReservations];
    }

    const bookingRefs = this.filteredReservations.map(r => r.bookingRef).filter(ref => !!ref);

    forkJoin({
      currRevenue: this.bookingService.getHotelRevenueSummary({
        hotelId: this.selectedHotel ? this.selectedHotel.id : undefined,
        bookingRefs,
        startDate: this.startDate,
        endDate: this.endDate
      }).pipe(catchError((err) => {
        console.warn('Current period revenue summary fetch failed:', err);
        return of(null);
      })),
      prevRevenue: this.bookingService.getHotelRevenueSummary({
        hotelId: this.selectedHotel ? this.selectedHotel.id : undefined,
        bookingRefs,
        startDate: this.prevStartDate,
        endDate: this.prevEndDate
      }).pipe(catchError((err) => {
        console.warn('Previous period revenue summary fetch failed:', err);
        return of(null);
      }))
    }).subscribe({
      next: (revRes) => {
        this.computeDashboardMetrics(currInventorySummary, prevInventorySummary, revRes.currRevenue, revRes.prevRevenue);
        this.isLoading = false;
      },
      error: () => {
        this.computeDashboardMetrics(currInventorySummary, prevInventorySummary, null, null);
        this.isLoading = false;
      }
    });
  }

  // ==================== ACCURATE DATE-AWARE METRIC CALCULATIONS ====================
  private computeDashboardMetrics(
    currInv: any,
    prevInv: any,
    currRevenueSummary: HotelRevenueReportResponse | null,
    prevRevenueSummary: HotelRevenueReportResponse | null
  ): void {
    // 0. Filter reservations for selected hotel (already filtered in filterAndFetchRevenue)
    // ================= 1. TOTAL BOOKINGS (CREATED IN PERIOD) =================
    const currBookings = this.filterBookingsCreatedInPeriod(this.filteredReservations, this.startDate, this.endDate);
    const prevBookings = this.filterBookingsCreatedInPeriod(this.filteredReservations, this.prevStartDate, this.prevEndDate);

    const currBookingsCount = currBookings.length;
    const prevBookingsCount = prevBookings.length;
    this.bookingsKpi = this.buildKpiComparison(currBookingsCount, prevBookingsCount);

    // ================= 2. ROOM NIGHTS (STAY OVERLAP IN PERIOD) =================
    const currRoomNights = this.calculatePeriodRoomNights(this.filteredReservations, this.startDate, this.endDate);
    const prevRoomNights = this.calculatePeriodRoomNights(this.filteredReservations, this.prevStartDate, this.prevEndDate);
    this.nightsKpi = this.buildKpiComparison(currRoomNights, prevRoomNights);

    // ================= 3. OCCUPANCY RATE (WP-03 DATE-WISE ARCHITECTURE) =================
    if (currInv) {
      this.totalPhysicalRoomNights = currInv.totalPhysicalRoomNights || 0;
      this.occupiedRoomNights = currInv.occupiedRoomNights || 0;
      this.heldRoomNights = currInv.heldRoomNights || 0;
      this.blockedRoomNights = currInv.blockedRoomNights || 0;
      this.availableRoomNights = currInv.availableRoomNights || 0;
      this.applicableRoomNightCapacity = currInv.applicableRoomNightCapacity || 0;
      this.occupancyRate = currInv.occupancyRate || 0;
    } else {
      // Fallback: derive date-wise from roomTypes and eligible room nights
      const totalRooms = this.roomTypes.reduce((sum, r) => sum + (Number(r.totalRooms) || 0), 0);
      this.totalPhysicalRoomNights = totalRooms * this.durationDays;
      this.occupiedRoomNights = currRoomNights;
      this.heldRoomNights = 0;
      this.blockedRoomNights = 0;
      this.applicableRoomNightCapacity = this.totalPhysicalRoomNights;
      this.availableRoomNights = Math.max(0, this.applicableRoomNightCapacity - this.occupiedRoomNights - this.heldRoomNights);
      this.occupancyRate = this.applicableRoomNightCapacity > 0
        ? Math.round(((this.occupiedRoomNights / this.applicableRoomNightCapacity) * 100) * 10) / 10
        : 0;
    }

    const prevOccupancyRate = prevInv ? (prevInv.occupancyRate || 0) : 0;
    this.occupancyKpi = this.buildKpiComparison(this.occupancyRate, prevOccupancyRate, true);
    this.donutOffset = 251.2 * (1 - (Math.min(100, Math.max(0, this.occupancyRate)) / 100));

    // ================= 4. NET REVENUE (ACTUAL TRANSACTION-BASED REVENUE) =================
    let currNetRevenue = 0;
    let prevNetRevenue = 0;

    if (currRevenueSummary) {
      currNetRevenue = currRevenueSummary.netRevenue;
    } else {
      const currRevenueData = this.calculatePeriodNetRevenue(this.filteredReservations, this.startDate, this.endDate);
      currNetRevenue = currRevenueData.netRevenue;
    }

    if (prevRevenueSummary) {
      prevNetRevenue = prevRevenueSummary.netRevenue;
    } else {
      const prevRevenueData = this.calculatePeriodNetRevenue(this.filteredReservations, this.prevStartDate, this.prevEndDate);
      prevNetRevenue = prevRevenueData.netRevenue;
    }

    this.revenueKpi = this.buildKpiComparison(currNetRevenue, prevNetRevenue);

    // ================= 5. BOOKING TRENDS CHART =================
    this.buildTrendsChartData(currInv, currRevenueSummary);

    // ================= 6. UPCOMING / REPORTING PERIOD RESERVATIONS =================
    // Filter strictly by check-in date within the reporting period; exclude CANCELLED, FAILED, EXPIRED.
    this.upcomingReservations = this.filteredReservations
      .filter(r => {
        if (r.status === 'CANCELLED' || r.status === 'FAILED' || r.status === 'EXPIRED') {
          return false;
        }
        const checkIn = r.checkInDate || this.extractDateFromBooking(r, 'in');
        return checkIn && checkIn >= this.startDate && checkIn <= this.endDate;
      })
      .sort((a, b) => {
        const dateA = a.checkInDate || this.extractDateFromBooking(a, 'in') || '';
        const dateB = b.checkInDate || this.extractDateFromBooking(b, 'in') || '';
        return dateA.localeCompare(dateB);
      })
      .slice(0, 5);

    // ================= 7. CORRECTED OPERATIONAL ALERTS =================
    this.computeOperationalAlerts(currInv);
  }

  // ==================== CALCULATION HELPERS ====================
  private filterBookingsCreatedInPeriod(bookings: any[], start: string, end: string): any[] {
    return bookings.filter(b => {
      // Exclude cancelled, failed, expired, unconfirmed
      if (b.status === 'CANCELLED' || b.status === 'FAILED' || b.status === 'EXPIRED') {
        return false;
      }
      const creationDate = b.bookingDate ? b.bookingDate.split('T')[0] : (b.confirmedAt ? b.confirmedAt.split('T')[0] : '');
      return creationDate >= start && creationDate <= end;
    });
  }

  private calculatePeriodRoomNights(bookings: any[], start: string, end: string): number {
    let nightsCount = 0;
    const startDate = new Date(start + 'T00:00:00');
    const endDate = new Date(end + 'T00:00:00');

    bookings.forEach(b => {
      // Exclude unpaid holds or cancelled bookings
      if (b.status !== 'CONFIRMED' && b.status !== 'CHECKED_IN' && b.status !== 'CHECKED_OUT' && b.status !== 'NO_SHOW') {
        return;
      }

      const checkInStr = b.checkInDate || this.extractDateFromBooking(b, 'in');
      const checkOutStr = b.checkOutDate || this.extractDateFromBooking(b, 'out');
      if (!checkInStr || !checkOutStr) return;

      const bCheckIn = new Date(checkInStr + 'T00:00:00');
      const bCheckOut = new Date(checkOutStr + 'T00:00:00');

      let cur = new Date(bCheckIn);
      let overlap = 0;
      // Check-in inclusive, checkout exclusive: [checkIn, checkOut)
      while (cur < bCheckOut) {
        if (cur >= startDate && cur <= endDate) {
          overlap++;
        }
        cur.setDate(cur.getDate() + 1);
      }

      const roomCount = this.extractRoomCountFromBooking(b);
      nightsCount += (overlap * roomCount);
    });

    return nightsCount;
  }

  private calculatePeriodNetRevenue(bookings: any[], start: string, end: string): { grossRevenue: number; refunds: number; netRevenue: number } {
    let grossRevenue = 0;
    let refunds = 0;

    bookings.forEach(b => {
      // 1. Eligible successful payment captured
      const hasSuccessfulPayment = b.status === 'CONFIRMED' ||
                                   b.status === 'CHECKED_IN' ||
                                   b.status === 'CHECKED_OUT' ||
                                   b.status === 'NO_SHOW' ||
                                   (b.status === 'CANCELLED' && b.confirmedAt);

      if (hasSuccessfulPayment) {
        const paymentDate = b.confirmedAt ? b.confirmedAt.split('T')[0] : (b.bookingDate ? b.bookingDate.split('T')[0] : '');
        if (paymentDate && paymentDate >= start && paymentDate <= end) {
          grossRevenue += (Number(b.totalAmount) || 0);
        }
      }

      // 2. Applicable refunds processed
      if (b.status === 'CANCELLED' && (b.refundStatus === 'PROCESSED' || (Number(b.refundAmount) || 0) > 0)) {
        const refundDate = b.cancelledAt ? b.cancelledAt.split('T')[0] : (b.bookingDate ? b.bookingDate.split('T')[0] : '');
        if (refundDate && refundDate >= start && refundDate <= end) {
          refunds += (Number(b.refundAmount) || 0);
        }
      }
    });

    const netRevenue = Math.max(0, grossRevenue - refunds);
    return { grossRevenue, refunds, netRevenue };
  }

  private computeOperationalAlerts(currInv: any): void {
    let arrivals = 0;
    let departures = 0;

    // 1. Arrivals: CONFIRMED reservations with checkInDate in reporting range (exclude PENDING_PAYMENT)
    this.filteredReservations.forEach(b => {
      const checkInStr = b.checkInDate || this.extractDateFromBooking(b, 'in');
      if (checkInStr && checkInStr >= this.startDate && checkInStr <= this.endDate) {
        if (b.status === 'CONFIRMED') {
          arrivals++;
        }
      }
    });

    // 2. Departures: CHECKED_IN guests with checkOutDate in reporting range (exclude already CHECKED_OUT)
    this.filteredReservations.forEach(b => {
      const checkOutStr = b.checkOutDate || this.extractDateFromBooking(b, 'out');
      if (checkOutStr && checkOutStr >= this.startDate && checkOutStr <= this.endDate) {
        if (b.status === 'CHECKED_IN') {
          departures++;
        }
      }
    });

    // 3. Active Room Blocks: Overlap rule [startDate, endDate)
    // block.startDate <= this.endDate && block.endDate > this.startDate
    const activeBlocks = this.roomBlocks.filter(b =>
      b.status === 'ACTIVE' &&
      b.startDate <= this.endDate &&
      b.endDate > this.startDate
    ).length;

    // 4. Low Availability: Unique room types with date-wise availableRooms <= 2 on any date in period
    let lowAvailCount = 0;
    if (currInv && currInv.dailySummaries) {
      // Check if daily available drops <= 2
      const hasLow = currInv.dailySummaries.some((d: any) => d.availableRooms !== undefined && d.availableRooms <= 2);
      lowAvailCount = hasLow ? Math.min(this.roomTypes.length, 2) : 0;
    } else {
      lowAvailCount = this.roomTypes.filter(rt =>
        rt.availableRooms !== undefined && rt.availableRooms !== null && rt.availableRooms <= 2
      ).length;
    }

    this.operationalAlerts = {
      arrivalsCount: arrivals,
      departuresCount: departures,
      activeRoomBlocksCount: activeBlocks,
      lowAvailabilityRoomsCount: lowAvailCount
    };
  }

  private buildTrendsChartData(currInv: any, currRev: HotelRevenueReportResponse | null = null): void {
    const rawItems: { isoStr: string; dateLabel: string; count: number; revenue: number }[] = [];
    const dStart = new Date(this.startDate + 'T00:00:00');
    const dEnd = new Date(this.endDate + 'T00:00:00');

    let maxCount = 0;
    let maxRev = 0;

    let cur = new Date(dStart);

    while (cur <= dEnd) {
      const isoStr = this.formatDateIso(cur);
      const dateLabel = this.formatDateShort(isoStr);

      // Bookings created on this day
      const dayBookings = this.filterBookingsCreatedInPeriod(this.filteredReservations, isoStr, isoStr);
      const dayCount = dayBookings.length;

      // Net revenue collected on this day from actual payment transactions
      let dayRev = 0;
      if (currRev && currRev.dailyBreakdown) {
        const dayItem = currRev.dailyBreakdown.find(d => d.date === isoStr);
        dayRev = dayItem ? dayItem.netRevenue : 0;
      } else {
        const dayRevenueData = this.calculatePeriodNetRevenue(this.filteredReservations, isoStr, isoStr);
        dayRev = dayRevenueData.netRevenue;
      }

      if (dayCount > maxCount) maxCount = dayCount;
      if (dayRev > maxRev) maxRev = dayRev;

      rawItems.push({ isoStr, dateLabel, count: dayCount, revenue: dayRev });
      cur.setDate(cur.getDate() + 1);
    }

    const totalDays = rawItems.length;

    // Adaptive step calculation based on total number of days:
    // Short ranges (<= 8 days): display all dates (step = 1)
    // Medium-short (9 - 16 days): every 2nd day (step = 2)
    // Medium (17 - 24 days): every 3rd day (step = 3)
    // Long (25 - 35 days, e.g. 30 days / 1 month): every 5th day (step = 5)
    // Very long (> 35 days): target 5 to 7 total labels
    let step = 1;
    if (totalDays <= 8) {
      step = 1;
    } else if (totalDays <= 16) {
      step = 2;
    } else if (totalDays <= 24) {
      step = 3;
    } else if (totalDays <= 35) {
      step = 5;
    } else {
      step = Math.ceil(totalDays / 6);
    }

    // Determine which days display an X-axis date label
    const shouldShow = new Array(totalDays).fill(false);
    if (totalDays <= 8) {
      shouldShow.fill(true);
    } else {
      let lastShown = -999;
      for (let i = 0; i < totalDays; i++) {
        const isStepMilestone = (i % step === 0);
        const isLast = (i === totalDays - 1);

        if (isStepMilestone) {
          // If milestone is too close to the final day, skip it so the final day displays without colliding
          if (totalDays - 1 - i >= Math.max(2, Math.floor(step * 0.7))) {
            shouldShow[i] = true;
            lastShown = i;
          }
        }

        if (isLast) {
          // Always show the last day if it has at least 2 days clearance from the previous milestone
          if (i - lastShown >= 2) {
            shouldShow[i] = true;
          }
        }
      }
    }

    this.maxChartValue = Math.max(40, Math.ceil((maxCount + 5) / 10) * 10);
    this.maxRevenueChartValue = Math.max(50000, Math.ceil((maxRev + 5000) / 10000) * 10000);

    const days: TrendDayItem[] = rawItems.map((item, idx) => ({
      dateLabel: item.dateLabel,
      fullDate: item.isoStr,
      count: item.count,
      revenue: item.revenue,
      heightPercent: item.count > 0
        ? Math.min(100, Math.max(2, Math.round((item.count / this.maxChartValue) * 100)))
        : 0,
      revenueHeightPercent: item.revenue > 0
        ? Math.min(100, Math.max(2, Math.round((item.revenue / this.maxRevenueChartValue) * 100)))
        : 0,
      heightPx: Math.max(4, Math.round((item.count / this.maxChartValue) * 200)),
      revenueHeightPx: item.revenue > 0
        ? Math.max(4, Math.round((item.revenue / this.maxRevenueChartValue) * 200))
        : 4,
      showLabel: shouldShow[idx]
    }));

    this.trendDays = days;
  }

  toggleBookingsTrend(mode: 'bookings' | 'revenue'): void {
    this.bookingsTrendToggle = mode;
  }

  buildKpiComparison(curr: number, prev: number, isPercentPoints: boolean = false, hasHistoricalData: boolean = true): KpiComparison {
    const diff = Math.round((curr - prev) * 10) / 10;
    if (!hasHistoricalData) {
      return {
        currentValue: curr,
        previousValue: prev,
        diff: 0,
        percentageChange: null,
        isPositive: false,
        isNegative: false,
        isNeutral: true,
        trendText: 'No prior data',
        hasData: false
      };
    }

    if (isPercentPoints) {
      const isPos = diff > 0;
      const isNeg = diff < 0;
      const isNeut = diff === 0;
      const sign = diff > 0 ? '+' : '';
      return {
        currentValue: curr,
        previousValue: prev,
        diff,
        percentageChange: diff,
        isPositive: isPos,
        isNegative: isNeg,
        isNeutral: isNeut,
        trendText: `${isPos ? '↑ ' : isNeg ? '↓ ' : ''}${sign}${diff} pp`,
        hasData: true
      };
    }

    if (prev === 0) {
      if (curr === 0) {
        return {
          currentValue: curr,
          previousValue: 0,
          diff: 0,
          percentageChange: 0,
          isPositive: false,
          isNegative: false,
          isNeutral: true,
          trendText: '0%',
          hasData: true
        };
      } else {
        return {
          currentValue: curr,
          previousValue: 0,
          diff: curr,
          percentageChange: 100,
          isPositive: true,
          isNegative: false,
          isNeutral: false,
          trendText: `↑ +${curr} (new)`,
          hasData: true
        };
      }
    }

    const pct = Math.round(((curr - prev) / prev) * 100);
    const isPos = pct > 0;
    const isNeg = pct < 0;
    const isNeut = pct === 0;
    const sign = pct > 0 ? '+' : '';
    return {
      currentValue: curr,
      previousValue: prev,
      diff,
      percentageChange: pct,
      isPositive: isPos,
      isNegative: isNeg,
      isNeutral: isNeut,
      trendText: `${isPos ? '↑ ' : isNeg ? '↓ ' : ''}${sign}${pct}%`,
      hasData: true
    };
  }

  private defaultKpiComparison(): KpiComparison {
    return {
      currentValue: 0,
      previousValue: 0,
      diff: 0,
      percentageChange: 0,
      isPositive: false,
      isNegative: false,
      isNeutral: true,
      trendText: '0%',
      hasData: false
    };
  }

  // ==================== NOTIFICATIONS ====================
  private initNotifications(): void {
    this.subscriptions.add(
      this.notificationService.notifications$.subscribe(notifications => {
        this.notificationList = notifications;
      })
    );
    this.subscriptions.add(
      this.notificationService.unreadCount$.subscribe(count => {
        this.unreadNotificationsCount = count;
      })
    );
  }

  toggleNotifications(event: Event): void {
    event.stopPropagation();
    this.showNotifications = !this.showNotifications;
    this.showUserDropdown = false;
    this.showPropertySelectorDropdown = false;
    this.showDateRangeDropdown = false;
  }

  markNotificationsRead(): void {
    this.notificationService.markAllAsRead().subscribe();
  }

  handleNotificationClick(n: FormattedNotification): void {
    if (!n.isRead) {
      this.notificationService.markAsRead(n.id).subscribe();
    }
    this.showNotifications = false;
  }

  // ==================== USER & PROFILE ====================
  toggleUserDropdown(event: Event): void {
    event.stopPropagation();
    this.showUserDropdown = !this.showUserDropdown;
    this.showNotifications = false;
    this.showPropertySelectorDropdown = false;
    this.showDateRangeDropdown = false;
  }

  togglePropertyDropdown(event: Event): void {
    event.stopPropagation();
    this.showPropertySelectorDropdown = !this.showPropertySelectorDropdown;
    this.showNotifications = false;
    this.showUserDropdown = false;
    this.showDateRangeDropdown = false;
  }

  toggleSidebar(): void {
    this.isSidebarCollapsed = !this.isSidebarCollapsed;
  }

  toggleMobileSidebar(): void {
    this.isMobileSidebarOpen = !this.isMobileSidebarOpen;
  }

  getInitials(): string {
    if (!this.currentUser?.fullName) return 'HM';
    const parts = this.currentUser.fullName.trim().split(' ');
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return parts[0].substring(0, 2).toUpperCase();
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  // ==================== QUICK ACTIONS & MODALS ====================
  openAddHotelModal(): void {
    this.showAddHotelModal = true;
    this.showPropertySelectorDropdown = false;
    this.showDateRangeDropdown = false;
  }

  submitAddHotel(): void {
    if (!this.currentUser) return;
    if (!this.hotelName.trim()) {
      alert('Please enter a hotel property name.');
      return;
    }

    const payload = {
      managerId: this.currentUser.id,
      managerName: this.currentUser.fullName,
      name: this.hotelName.trim(),
      city: this.hotelCity.trim(),
      address: this.hotelAddress.trim(),
      starRating: this.hotelStarRating,
      description: this.hotelDescription.trim(),
      amenities: this.hotelAmenities.trim(),
      imageUrl: this.hotelImageUrl.trim()
    };

    this.hotelService.addHotel(payload).subscribe({
      next: () => {
        alert('Hotel Property registered successfully!');
        this.showAddHotelModal = false;
        this.hotelName = '';
        this.hotelAddress = '';
        this.hotelDescription = '';
        this.loadInitialData();
      },
      error: (err) => {
        console.error('Add hotel error:', err);
        alert(err.error?.message || 'Failed to register hotel property.');
      }
    });
  }

  openAddRoomModal(hotel?: any): void {
    this.selectedHotelForRoom = hotel || this.selectedHotel;
    if (!this.selectedHotelForRoom && this.myHotels.length > 0) {
      this.selectedHotelForRoom = this.myHotels[0];
    }
    if (!this.selectedHotelForRoom) {
      alert('Please register or select a hotel property before adding room types.');
      return;
    }
    this.showAddRoomModal = true;
  }

  submitAddRoomType(): void {
    if (!this.selectedHotelForRoom) return;
    if (!this.roomTypeName.trim()) {
      alert('Please enter a room category name.');
      return;
    }

    const payload = {
      roomTypeName: this.roomTypeName.trim(),
      maxOccupancy: this.roomMaxOccupancy,
      pricePerNight: this.roomPricePerNight,
      totalRooms: this.roomTotalCount,
      availableRooms: this.roomTotalCount,
      amenities: this.roomAmenities
    };

    this.hotelService.addRoomType(this.selectedHotelForRoom.id, payload).subscribe({
      next: () => {
        alert('Room Type added successfully!');
        this.showAddRoomModal = false;
        this.loadDashboardData();
      },
      error: (err) => {
        console.error('Add room type error:', err);
        alert(err.error?.message || 'Failed to add room type.');
      }
    });
  }

  openBlockRoomModal(): void {
    if (!this.selectedHotel) {
      alert('Please select a hotel property first.');
      return;
    }
    if (this.roomTypes.length === 0) {
      alert('Please add at least one room category to this hotel before creating a block.');
      return;
    }
    this.blockRoomId = this.roomTypes[0].id;
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const dayAfter = new Date();
    dayAfter.setDate(dayAfter.getDate() + 2);
    this.blockStartDate = this.formatDateIso(tomorrow);
    this.blockEndDate = this.formatDateIso(dayAfter);
    this.showBlockRoomModal = true;
  }

  submitBlockRoom(): void {
    if (!this.selectedHotel || !this.blockRoomId) return;
    if (!this.blockStartDate || !this.blockEndDate) {
      alert('Please specify valid start and end dates.');
      return;
    }

    const payload = {
      blockedCount: this.blockCount,
      startDate: this.blockStartDate,
      endDate: this.blockEndDate,
      reason: this.blockReason,
      notes: this.blockNotes
    };

    this.hotelService.createRoomBlock(this.selectedHotel.id, this.blockRoomId, payload).subscribe({
      next: () => {
        alert('Room Block created successfully!');
        this.showBlockRoomModal = false;
        this.loadDashboardData();
      },
      error: (err) => {
        console.error('Create room block error:', err);
        alert(err.error?.message || 'Failed to create room block.');
      }
    });
  }

  openPriceOverrideModal(): void {
    if (!this.selectedHotel) {
      alert('Please select a hotel property first.');
      return;
    }
    if (this.roomTypes.length === 0) {
      alert('Please add at least one room category before updating pricing.');
      return;
    }
    this.overrideRoomId = this.roomTypes[0].id;
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const dayAfter = new Date();
    dayAfter.setDate(dayAfter.getDate() + 5);
    this.overrideStartDate = this.formatDateIso(tomorrow);
    this.overrideEndDate = this.formatDateIso(dayAfter);
    this.showPriceOverrideModal = true;
  }

  submitPriceOverride(): void {
    if (!this.overrideRoomId || !this.overrideStartDate || !this.overrideEndDate) {
      alert('Please specify a room category and valid date range.');
      return;
    }

    const payload = {
      startDate: this.overrideStartDate,
      endDate: this.overrideEndDate,
      pricePerNight: this.overridePrice
    };

    this.hotelService.setPriceOverrideRange(this.overrideRoomId, payload).subscribe({
      next: () => {
        alert('Price override updated successfully!');
        this.showPriceOverrideModal = false;
        this.loadDashboardData();
      },
      error: (err) => {
        console.error('Set price override error:', err);
        alert(err.error?.message || 'Failed to update price override.');
      }
    });
  }

  // ==================== RESERVATIONS OPERATIONS ====================
  openReservationDetails(res: any): void {
    this.selectedReservation = res;
    this.showReservationDetailsModal = true;
  }

  checkInGuest(res: any): void {
    if (!res?.bookingRef) return;
    this.bookingService.checkInGuest(res.bookingRef).subscribe({
      next: () => {
        alert(`Guest ${res.customerName} checked in successfully!`);
        this.loadDashboardData();
        if (this.selectedReservation) this.selectedReservation.status = 'CHECKED_IN';
      },
      error: (err) => alert(err.error?.message || 'Check-in failed')
    });
  }

  checkOutGuest(res: any): void {
    if (!res?.bookingRef) return;
    this.bookingService.checkOutGuest(res.bookingRef).subscribe({
      next: () => {
        alert(`Guest ${res.customerName} checked out successfully!`);
        this.loadDashboardData();
        if (this.selectedReservation) this.selectedReservation.status = 'CHECKED_OUT';
      },
      error: (err) => alert(err.error?.message || 'Check-out failed')
    });
  }

  markNoShow(res: any): void {
    if (!res?.bookingRef) return;
    if (confirm(`Mark reservation ${res.bookingRef} as NO-SHOW? Any reserved dates will be returned to inventory.`)) {
      this.bookingService.markNoShow(res.bookingRef).subscribe({
        next: () => {
          alert('Reservation marked as NO-SHOW. Inventory released.');
          this.loadDashboardData();
          if (this.selectedReservation) this.selectedReservation.status = 'NO_SHOW';
        },
        error: (err) => alert(err.error?.message || 'Marking no-show failed')
      });
    }
  }

  // ==================== FORMATTERS & UTILITIES ====================
  formatCurrency(val: number): string {
    if (val === undefined || val === null) return '₹0';
    const num = Number(val);
    if (num < 0) {
      return '-₹' + Math.abs(num).toLocaleString('en-IN');
    }
    return '₹' + num.toLocaleString('en-IN');
  }

  formatDateIso(date: Date): string {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  formatDateShort(dateStr: string): string {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    if (parts.length < 3) return dateStr;
    const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const month = monthNames[parseInt(parts[1], 10) - 1];
    const day = parseInt(parts[2], 10);
    return `${month} ${day}`;
  }

  formatDate(dateStr: string | null | undefined): string {
    if (!dateStr) return 'N/A';
    try {
      const d = new Date(dateStr);
      if (isNaN(d.getTime())) return dateStr;
      const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      return `${monthNames[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()}`;
    } catch {
      return dateStr;
    }
  }

  extractRoomTypeFromBooking(res: any): string {
    if (!res) return 'Standard Room';
    if (res.detailsJson) {
      try {
        const parsed = typeof res.detailsJson === 'string' ? JSON.parse(res.detailsJson) : res.detailsJson;
        if (parsed.roomTypeName) return parsed.roomTypeName;
        if (parsed.roomType) return parsed.roomType;
        if (parsed.category) return parsed.category;
      } catch {
        if (typeof res.detailsJson === 'string' && res.detailsJson.trim().length > 0 && !res.detailsJson.startsWith('{')) {
          return res.detailsJson;
        }
      }
    }
    if (res.serviceTitle && res.serviceTitle.includes('-')) {
      const parts = res.serviceTitle.split('-');
      if (parts.length > 1) return parts[1].trim();
    }
    return 'Deluxe Room';
  }

  private extractRoomCountFromBooking(res: any): number {
    if (!res?.detailsJson) return 1;
    try {
      const parsed = typeof res.detailsJson === 'string' ? JSON.parse(res.detailsJson) : res.detailsJson;
      return parsed.roomCount || parsed.roomsBooked || parsed.count || 1;
    } catch {
      return 1;
    }
  }

  private extractDateFromBooking(res: any, type: 'in' | 'out'): string | null {
    if (!res?.detailsJson) return null;
    try {
      const parsed = typeof res.detailsJson === 'string' ? JSON.parse(res.detailsJson) : res.detailsJson;
      if (type === 'in') return parsed.checkInDate || parsed.checkIn || null;
      if (type === 'out') return parsed.checkOutDate || parsed.checkOut || null;
    } catch {
      return null;
    }
    return null;
  }

  getStatusBadgeClass(status: string): string {
    switch (status) {
      case 'CONFIRMED': return 'badge-status-confirmed';
      case 'PENDING_PAYMENT':
      case 'INITIATED': return 'badge-status-pending';
      case 'CHECKED_IN': return 'badge-status-checkedin';
      case 'CHECKED_OUT': return 'badge-status-checkedout';
      case 'CANCELLED': return 'badge-status-cancelled';
      case 'NO_SHOW': return 'badge-status-noshow';
      default: return 'badge-status-checkedout';
    }
  }

  getStatusDisplay(status: string): string {
    switch (status) {
      case 'CONFIRMED': return 'Confirmed';
      case 'PENDING_PAYMENT': return 'Pending Payment';
      case 'CHECKED_IN': return 'Checked In';
      case 'CHECKED_OUT': return 'Checked Out';
      case 'CANCELLED': return 'Cancelled';
      case 'NO_SHOW': return 'No Show';
      default: return status || 'Unknown';
    }
  }

  // ==================== PROPERTY REGISTRATION WIZARD METHODS (WP-05 STEP 1) ====================

  startPropertyRegistration(draft?: any): void {
    this.propertyViewMode = 'wizard';
    this.currentWizardStep = 1;
    this.step1Submitted = false;
    this.step1Submitting = false;
    this.step1ErrorMessage = null;
    this.step1SuccessMessage = null;
    this.isStep1Dirty = false;
    this.isStep2Dirty = false;
    this.step2Submitted = false;
    this.step2Submitting = false;
    this.step2ErrorMessage = null;
    this.imageUploadErrors = ['', '', '', ''];
    this.imageUploading = [false, false, false, false];
    this.editingFromReview = false;
    this.accuracyConfirmed = false;
    this.step5Submitted = false;
    this.step5Submitting = false;
    this.step5ErrorMessage = null;
    this.registrationSuccess = false;
    this.activatedPropertyDetails = null;
    this.showTermsModal = false;
    this.reviewSectionsExpanded = {
      basic: true,
      address: false,
      propertyInfo: false,
      facilities: false
    };

    if (draft) {
      let segment = draft.propertySegment || '';
      const upperSeg = segment.trim().toUpperCase().replace(/_/g, ' ');
      if (upperSeg === 'BUDGET' || upperSeg === 'STANDARD') segment = 'Standard';
      else if (upperSeg === 'MIDSCALE' || upperSeg === 'COMFORT') segment = 'Comfort';
      else if (upperSeg === 'UPSCALE' || upperSeg === 'PREMIUM') segment = 'Premium';
      else if (upperSeg === 'LUXURY') segment = 'Luxury';
      else if (upperSeg === 'ULTRA LUXURY') segment = 'Ultra Luxury';

      let classification = draft.propertyClassification || '';
      const upperClass = classification.trim().toUpperCase().replace(/ /g, '_');
      let customClass = draft.customClassification || '';
      if (upperClass === 'NOT_OFFICIALLY_CLASSIFIED' || upperClass === 'NOT_CLASSIFIED') {
        classification = 'NOT_CLASSIFIED';
      } else if (upperClass === 'OTHER_RECOGNIZED_CLASSIFICATION' || upperClass === 'OTHER') {
        classification = 'OTHER';
      } else if (upperClass === 'HERITAGE') {
        classification = 'OTHER';
        if (!customClass) customClass = 'Heritage Hotel';
      }

      this.step1Data = {
        id: draft.id || null,
        name: draft.name || '',
        propertyType: draft.propertyType || '',
        propertyClassification: classification,
        customClassification: customClass,
        propertySegment: segment,
        brandName: draft.brandName || '',
        description: draft.description || ''
      };
      this.step2Data = {
        addressLine1: draft.addressLine1 || '',
        addressLine2: draft.addressLine2 || '',
        country: draft.country || 'India',
        state: draft.state || '',
        city: draft.city || '',
        postalCode: draft.postalCode || '',
        landmark: draft.landmark || ''
      };
      this.updateAvailableStates();

      this.step3Data = {
        propertyContactNumber: draft.propertyContactNumber || '',
        alternateContactNumber: draft.alternateContactNumber || '',
        propertyEmail: draft.propertyEmail || '',
        websiteUrl: draft.websiteUrl || '',
        registeredBusinessName: draft.registeredBusinessName || '',
        businessRegistrationType: draft.businessRegistrationType || 'Private Limited Company',
        otherBusinessRegistrationType: draft.otherBusinessRegistrationType || '',
        businessRegistrationNumber: draft.businessRegistrationNumber || '',
        registeredBusinessAddress: draft.registeredBusinessAddress || '',
        isGstRegistered: draft.isGstRegistered !== undefined ? !!draft.isGstRegistered : false,
        gstin: draft.gstin || '',
        gstLegalName: draft.gstLegalName || '',
        gstState: draft.gstState || '',
        propertyManagerName: draft.propertyManagerName || '',
        propertyManagerDesignation: draft.propertyManagerDesignation || 'General Manager',
        propertyManagerContactNumber: draft.propertyManagerContactNumber || '',
        propertyManagerEmail: draft.propertyManagerEmail || '',
        checkInTime: draft.checkInTime || '14:00',
        checkOutTime: draft.checkOutTime || '11:00',
        receptionAvailability: (draft.receptionAvailability && draft.receptionAvailability.toUpperCase().includes('LIMITED')) ? 'Limited Operating Hours' : '24-hour Reception',
        receptionOpeningTime: draft.receptionOpeningTime || '',
        receptionClosingTime: draft.receptionClosingTime || ''
      };


      this.step4Data = {
        predefinedAmenities: draft.predefinedAmenities && Array.isArray(draft.predefinedAmenities) ? [...draft.predefinedAmenities] : [],
        customAmenities: draft.customAmenities && Array.isArray(draft.customAmenities) ? [...draft.customAmenities] : [],
        childrenPolicy: draft.childrenPolicy || 'Children Allowed',
        petPolicy: draft.petPolicy || 'Pets Not Allowed',
        smokingPolicy: draft.smokingPolicy || 'Smoking Prohibited',
        extraBedPolicy: draft.extraBedPolicy || 'Extra Bed Available',
        acceptedIdentificationTypes: draft.acceptedIdentificationTypes && Array.isArray(draft.acceptedIdentificationTypes) && draft.acceptedIdentificationTypes.length
          ? [...draft.acceptedIdentificationTypes]
          : ['Passport', 'Aadhaar Card'],
        customIdentificationType: draft.customIdentificationType || '',
        additionalRules: draft.additionalRules || ''
      };

      if (draft.registrationStep && draft.registrationStep >= 5) {
        this.currentWizardStep = 5;
      } else if (draft.registrationStep && draft.registrationStep >= 4) {
        this.currentWizardStep = 4;
      } else if (draft.registrationStep && draft.registrationStep >= 3) {
        this.currentWizardStep = 3;

      } else if (draft.registrationStep && draft.registrationStep >= 2) {
        this.currentWizardStep = 2;
      } else {
        this.currentWizardStep = 1;
      }
      this.wizardImages = [null, null, null, null];
      if (draft.images && Array.isArray(draft.images)) {
        draft.images.forEach((img: any, idx: number) => {
          if (idx < 4) {
            this.wizardImages[idx] = {
              id: img.id,
              imageUrl: img.imageUrl,
              originalFileName: img.originalFileName,
              isCover: img.isCover !== undefined ? img.isCover : (img.cover !== undefined ? img.cover : (idx === 0)),
              cover: img.isCover !== undefined ? img.isCover : (img.cover !== undefined ? img.cover : (idx === 0)),
              displayOrder: img.displayOrder !== undefined ? img.displayOrder : idx
            };
          }
        });
      }
    } else {
      this.step1Data = {
        id: null,
        name: '',
        propertyType: '',
        propertyClassification: '',
        customClassification: '',
        propertySegment: '',
        brandName: '',
        description: ''
      };
      this.step2Data = {
        addressLine1: '',
        addressLine2: '',
        country: 'India',
        state: '',
        city: '',
        postalCode: '',
        landmark: ''
      };
      this.updateAvailableStates();

      this.step3Data = {
        propertyContactNumber: '',
        alternateContactNumber: '',
        propertyEmail: '',
        websiteUrl: '',
        registeredBusinessName: '',
        businessRegistrationType: 'Private Limited Company',
        otherBusinessRegistrationType: '',
        businessRegistrationNumber: '',
        registeredBusinessAddress: '',
        isGstRegistered: false,
        gstin: '',
        gstLegalName: '',
        gstState: '',
        propertyManagerName: '',
        propertyManagerDesignation: 'General Manager',
        propertyManagerContactNumber: '',
        propertyManagerEmail: '',
        checkInTime: '14:00',
        checkOutTime: '11:00',
        receptionAvailability: '24-hour Reception',
        receptionOpeningTime: '',
        receptionClosingTime: ''
      };
      this.expandedStep3Section = 'contact';
      this.isStep3Dirty = false;
      this.step3Submitted = false;
      this.step3Submitting = false;
      this.step3ErrorMessage = null;
      this.currentWizardStep = 1;
      this.wizardImages = [null, null, null, null];
    }
  }

  loadManagerDrafts(): void {
    this.hotelService.getManagerDrafts().subscribe({
      next: (drafts) => {
        this.managerDrafts = drafts || [];
      },
      error: (err) => console.warn('Failed to load drafts:', err)
    });
  }

  onStep1FieldChange(): void {
    this.isStep1Dirty = true;
    this.step1ErrorMessage = null;
  }

  onClassificationChange(): void {
    this.isStep1Dirty = true;
    this.step1ErrorMessage = null;
    if (this.step1Data.propertyClassification !== 'OTHER') {
      this.step1Data.customClassification = '';
    }
  }

  onDescriptionInput(event: any): void {
    const val: string = event.target.value || '';
    this.step1Data.description = val.length > 300 ? val.substring(0, 300) : val;
    this.isStep1Dirty = true;
    this.step1ErrorMessage = null;
  }

  isPropertyNameValid(): boolean {
    return !!this.step1Data.name && this.step1Data.name.trim().length > 0 && this.step1Data.name.trim().length <= 255;
  }

  isPropertyTypeValid(): boolean {
    return !!this.step1Data.propertyType && this.step1Data.propertyType.trim().length > 0;
  }

  isClassificationValid(): boolean {
    if (this.step1Data.propertyClassification === 'OTHER') {
      return !!this.step1Data.customClassification && this.step1Data.customClassification.trim().length > 0;
    }
    return true;
  }

  isPropertySegmentValid(): boolean {
    return !!this.step1Data.propertySegment && this.step1Data.propertySegment.trim().length > 0;
  }

  isDescriptionValid(): boolean {
    return !!this.step1Data.description && this.step1Data.description.trim().length > 0 && this.step1Data.description.trim().length <= 300;
  }

  isCoverImageValid(): boolean {
    return this.wizardImages.some(img => img !== null && img.isCover && !!(img.imageUrl || img.previewUrl));
  }

  isStep1FormValid(): boolean {
    return this.isPropertyNameValid() &&
           this.isPropertyTypeValid() &&
           this.isClassificationValid() &&
           this.isPropertySegmentValid() &&
           this.isDescriptionValid() &&
           this.isCoverImageValid();
  }

  getUploadedImagesCount(): number {
    return this.wizardImages.filter(img => img !== null && (!!img.imageUrl || !!img.previewUrl)).length;
  }

  onImageFileSelected(event: any, slotIndex: number): void {
    const file = event.target.files?.[0];
    if (!file) return;

    this.imageUploadErrors[slotIndex] = '';

    // Validate size <= 1 MB (1,048,576 bytes)
    if (file.size > 1048576) {
      const sizeMb = (file.size / (1024 * 1024)).toFixed(1);
      this.imageUploadErrors[slotIndex] = `File exceeds 1 MB limit (${sizeMb} MB). Please choose a smaller image.`;
      event.target.value = '';
      return;
    }

    // Validate format: JPG, JPEG, PNG, WebP
    const allowedMimeTypes = ['image/jpeg', 'image/jpg', 'image/pjpeg', 'image/png', 'image/webp'];
    const fileName = (file.name || '').toLowerCase();
    const hasAllowedExt = fileName.endsWith('.jpg') || fileName.endsWith('.jpeg') || fileName.endsWith('.png') || fileName.endsWith('.webp');
    const isMimeOk = file.type && allowedMimeTypes.includes(file.type.toLowerCase());

    if (!isMimeOk && !hasAllowedExt) {
      this.imageUploadErrors[slotIndex] = 'Unsupported format. Allowed formats are JPG, JPEG, PNG, or WebP.';
      event.target.value = '';
      return;
    }

    // Local preview reader for immediate UX
    const reader = new FileReader();
    reader.onload = (e: any) => {
      const previewUrl = e.target.result;
      const isCover = slotIndex === 0 || !this.wizardImages.some(img => img !== null && img.isCover);

      this.wizardImages[slotIndex] = {
        imageUrl: '',
        previewUrl: previewUrl,
        originalFileName: file.name,
        isCover: isCover,
        displayOrder: slotIndex,
        file: file,
        uploading: true
      };
      this.isStep1Dirty = true;

      // Upload to backend
      this.imageUploading[slotIndex] = true;
      this.hotelService.uploadHotelImage(file, isCover ? 'cover' : 'gallery').subscribe({
        next: (res) => {
          this.imageUploading[slotIndex] = false;
          if (this.wizardImages[slotIndex]) {
            this.wizardImages[slotIndex]!.imageUrl = res.imageUrl;
            this.wizardImages[slotIndex]!.uploading = false;
          }
        },
        error: (err) => {
          this.imageUploading[slotIndex] = false;
          if (this.wizardImages[slotIndex]) {
            this.wizardImages[slotIndex]!.uploading = false;
          }
          this.imageUploadErrors[slotIndex] = err.error?.message || 'Failed to upload image. Please try again.';
        }
      });
    };
    reader.readAsDataURL(file);
    event.target.value = '';
  }

  removeImage(slotIndex: number, event: MouseEvent): void {
    event.stopPropagation();
    const img = this.wizardImages[slotIndex];
    if (img && img.imageUrl) {
      const filename = img.imageUrl.substring(img.imageUrl.lastIndexOf('/') + 1);
      this.hotelService.deleteHotelImage(filename).subscribe({
        next: () => {},
        error: (err) => console.warn('Failed to delete image from server:', err)
      });
    }

    const wasCover = img?.isCover;
    this.wizardImages[slotIndex] = null;
    this.imageUploadErrors[slotIndex] = '';
    this.isStep1Dirty = true;

    // If removed image was cover, assign cover to the first available image
    if (wasCover) {
      const nextImg = this.wizardImages.find(i => i !== null);
      if (nextImg) {
        nextImg.isCover = true;
      }
    }
  }

  setAsCover(slotIndex: number, event: MouseEvent): void {
    event.stopPropagation();
    const current = this.wizardImages[slotIndex];
    if (!current) return;

    this.wizardImages.forEach(img => {
      if (img) img.isCover = false;
    });
    current.isCover = true;
    this.isStep1Dirty = true;
  }

  proceedToStep2(): void {
    this.step1Submitted = true;
    this.step1ErrorMessage = null;

    if (!this.isStep1FormValid()) {
      if (!this.isCoverImageValid()) {
        this.step1ErrorMessage = 'Please upload a mandatory cover image (Tile 1) to proceed.';
      } else {
        this.step1ErrorMessage = 'Please complete all required fields correctly.';
      }
      return;
    }

    // Check if any image is still uploading
    if (this.imageUploading.some(u => u)) {
      this.step1ErrorMessage = 'Please wait for all images to finish uploading.';
      return;
    }

    this.step1Submitting = true;

    const imagesToSave = this.wizardImages
      .filter(img => img !== null && (!!img.imageUrl || !!img.previewUrl))
      .map((img, idx) => ({
        imageUrl: img!.imageUrl || img!.previewUrl || '',
        originalFileName: img!.originalFileName,
        isCover: !!img!.isCover,
        cover: !!img!.isCover,
        displayOrder: idx
      }));

    const payload: HotelStep1Request = {
      id: this.step1Data.id,
      name: this.step1Data.name.trim(),
      propertyType: this.step1Data.propertyType.trim(),
      propertyClassification: this.step1Data.propertyClassification || null,
      customClassification: this.step1Data.propertyClassification === 'OTHER' ? this.step1Data.customClassification.trim() : null,
      propertySegment: this.step1Data.propertySegment.trim(),
      brandName: this.step1Data.brandName ? this.step1Data.brandName.trim() : null,
      description: this.step1Data.description.trim(),
      images: imagesToSave
    };

    this.hotelService.saveStep1Draft(payload).subscribe({
      next: (res) => {
        this.step1Submitting = false;
        this.step1Data.id = res.id;
        this.isStep1Dirty = false;
        if (this.editingFromReview) {
          this.editingFromReview = false;
          this.currentWizardStep = 5;
        } else {
          this.currentWizardStep = 2;
        }
      },
      error: (err) => {
        this.step1Submitting = false;
        console.error('Save Step 1 draft error:', err);
        this.step1ErrorMessage = err.error?.message || 'Failed to save property draft. Please check your entries and try again.';
      }
    });
  }

  backToStep1(): void {
    this.currentWizardStep = 1;
  }

  confirmCancelWizard(): void {
    if (this.isStep1Dirty || this.isStep2Dirty || this.isStep3Dirty) {
      this.showCancelWizardModal = true;
    } else {
      this.propertyViewMode = 'list';
    }
  }

  discardAndExitWizard(): void {
    this.showCancelWizardModal = false;
    this.propertyViewMode = 'list';
    this.loadInitialData();
  }

  saveDraftAndExitWizard(): void {
    if (this.step1Data.name && this.step1Data.name.trim().length > 0 && this.isCoverImageValid()) {
      this.proceedToStep2();
      this.showCancelWizardModal = false;
      this.propertyViewMode = 'list';
      this.loadInitialData();
    } else {
      this.showCancelWizardModal = false;
      this.propertyViewMode = 'list';
    }
  }


  onCountryChange(): void {
    this.isStep2Dirty = true;
    this.step2ErrorMessage = null;
    this.updateAvailableStates();
    if (this.availableStates.length > 0 && !this.availableStates.includes(this.step2Data.state)) {
      this.step2Data.state = '';
    }
  }

  updateAvailableStates(): void {
    this.availableStates = this.countryStatesMap[this.step2Data.country] || [];
  }

  onStep2FieldChange(): void {
    this.isStep2Dirty = true;
    this.step2ErrorMessage = null;
  }

  isAddressLine1Valid(): boolean {
    return !!this.step2Data.addressLine1 && this.step2Data.addressLine1.trim().length > 0 && this.step2Data.addressLine1.trim().length <= 255;
  }

  isCountryValid(): boolean {
    return !!this.step2Data.country && this.step2Data.country.trim().length > 0;
  }

  isStateValid(): boolean {
    if (!this.step2Data.state || !this.step2Data.state.trim()) return false;
    if (this.availableStates.length > 0) {
      return this.availableStates.includes(this.step2Data.state.trim());
    }
    return this.step2Data.state.trim().length <= 100;
  }

  isCityValid(): boolean {
    return !!this.step2Data.city && this.step2Data.city.trim().length > 0 && this.step2Data.city.trim().length <= 255;
  }

  isPostalCodeValid(): boolean {
    if (!this.step2Data.postalCode || !this.step2Data.postalCode.trim()) return false;
    const code = this.step2Data.postalCode.trim();
    if (this.step2Data.country === 'India') {
      return /^[1-9][0-9]{5}$/.test(code);
    }
    return /^[a-zA-Z0-9\s-]{3,10}$/.test(code);
  }

  isStep2FormValid(): boolean {
    return this.isAddressLine1Valid() &&
           this.isCountryValid() &&
           this.isStateValid() &&
           this.isCityValid() &&
           this.isPostalCodeValid();
  }

  proceedToStep3(): void {
    this.step2Submitted = true;
    this.step2ErrorMessage = null;

    if (!this.isStep2FormValid()) {
      if (!this.isPostalCodeValid() && this.step2Data.country === 'India' && !!this.step2Data.postalCode) {
        this.step2ErrorMessage = 'Please enter a valid 6-digit PIN code for India (e.g., 520010).';
      } else {
        this.step2ErrorMessage = 'Please complete all required fields correctly.';
      }
      return;
    }

    if (!this.step1Data.id) {
      this.step2ErrorMessage = 'Draft ID missing. Please return to Step 1 and save basic details first.';
      return;
    }

    this.step2Submitting = true;
    const payload: HotelStep2Request = {
      draftId: this.step1Data.id,
      addressLine1: this.step2Data.addressLine1.trim(),
      addressLine2: this.step2Data.addressLine2 ? this.step2Data.addressLine2.trim() : null,
      country: this.step2Data.country.trim(),
      state: this.step2Data.state.trim(),
      city: this.step2Data.city.trim(),
      postalCode: this.step2Data.postalCode.trim(),
      landmark: this.step2Data.landmark ? this.step2Data.landmark.trim() : null
    };

    this.hotelService.saveStep2Draft(this.step1Data.id, payload).subscribe({
      next: (res) => {
        this.step2Submitting = false;
        this.isStep2Dirty = false;
        if (this.editingFromReview) {
          this.editingFromReview = false;
          this.currentWizardStep = 5;
        } else {
          this.currentWizardStep = 3;
        }
      },
      error: (err) => {
        this.step2Submitting = false;
        console.error('Save Step 2 draft error:', err);
        this.step2ErrorMessage = err.error?.message || 'Failed to save location & address details. Please check your entries and try again.';
      }
    });
  }

  backToStep2(): void {
    this.currentWizardStep = 2;
  }

  // =========================================================================
  // WP-05 STEP 3: PROPERTY INFORMATION METHODS
  // =========================================================================

  toggleStep3Section(section: string): void {
    if (this.expandedStep3Section === section) {
      this.expandedStep3Section = ''; // collapse currently expanded
    } else {
      this.expandedStep3Section = section; // expand chosen subsection
    }
  }

  isStep3SectionExpanded(section: string): boolean {
    return this.expandedStep3Section === section;
  }

  onStep3FieldChange(): void {
    this.isStep3Dirty = true;
    this.step3ErrorMessage = null;
  }

  onGstToggle(registered: boolean): void {
    this.step3Data.isGstRegistered = registered;
    this.isStep3Dirty = true;
    this.step3ErrorMessage = null;
    if (!registered) {
      this.step3Data.gstin = '';
      this.step3Data.gstLegalName = '';
      this.step3Data.gstState = '';
    }
  }

  // --- Subsection 1: Property Contact Details Validation ---

  isPropertyContactNumberValid(): boolean {
    if (!this.step3Data.propertyContactNumber || !this.step3Data.propertyContactNumber.trim()) return false;
    const phone = this.step3Data.propertyContactNumber.trim();
    return /^[+]?[0-9\s\-()]{7,20}$/.test(phone);
  }

  isAlternateContactNumberValid(): boolean {
    if (!this.step3Data.alternateContactNumber || !this.step3Data.alternateContactNumber.trim()) return true;
    const phone = this.step3Data.alternateContactNumber.trim();
    return /^[+]?[0-9\s\-()]{7,20}$/.test(phone);
  }

  isPropertyEmailValid(): boolean {
    if (!this.step3Data.propertyEmail || !this.step3Data.propertyEmail.trim()) return false;
    const email = this.step3Data.propertyEmail.trim();
    return /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(email);
  }

  isWebsiteUrlValid(): boolean {
    if (!this.step3Data.websiteUrl || !this.step3Data.websiteUrl.trim()) return true;
    const url = this.step3Data.websiteUrl.trim().toLowerCase();
    return url.startsWith('http://') || url.startsWith('https://');
  }

  isContactSectionValid(): boolean {
    return this.isPropertyContactNumberValid() &&
           this.isAlternateContactNumberValid() &&
           this.isPropertyEmailValid() &&
           this.isWebsiteUrlValid();
  }

  // --- Subsection 2: Business Registration Details Validation ---

  isRegisteredBusinessNameValid(): boolean {
    return !!this.step3Data.registeredBusinessName &&
           this.step3Data.registeredBusinessName.trim().length > 0 &&
           this.step3Data.registeredBusinessName.trim().length <= 255;
  }

  isBusinessRegistrationTypeValid(): boolean {
    return !!this.step3Data.businessRegistrationType && this.step3Data.businessRegistrationType.trim().length > 0;
  }

  isOtherBusinessRegistrationTypeValid(): boolean {
    if (this.step3Data.businessRegistrationType === 'Other') {
      return !!this.step3Data.otherBusinessRegistrationType && this.step3Data.otherBusinessRegistrationType.trim().length > 0;
    }
    return true;
  }

  isBusinessRegistrationNumberValid(): boolean {
    return !!this.step3Data.businessRegistrationNumber &&
           this.step3Data.businessRegistrationNumber.trim().length > 0 &&
           this.step3Data.businessRegistrationNumber.trim().length <= 100;
  }

  isBusinessSectionValid(): boolean {
    return this.isRegisteredBusinessNameValid() &&
           this.isBusinessRegistrationTypeValid() &&
           this.isOtherBusinessRegistrationTypeValid() &&
           this.isBusinessRegistrationNumberValid();
  }

  // --- Subsection 3: GST Details Validation ---

  isGstinValid(): boolean {
    if (!this.step3Data.isGstRegistered) return true;
    if (!this.step3Data.gstin || !this.step3Data.gstin.trim()) return false;
    const gstin = this.step3Data.gstin.trim().toUpperCase();
    return /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/.test(gstin);
  }

  isGstLegalNameValid(): boolean {
    if (!this.step3Data.isGstRegistered) return true;
    return !!this.step3Data.gstLegalName && this.step3Data.gstLegalName.trim().length > 0;
  }

  isGstStateValid(): boolean {
    if (!this.step3Data.isGstRegistered) return true;
    return !!this.step3Data.gstState && this.step3Data.gstState.trim().length > 0;
  }

  isGstSectionValid(): boolean {
    if (!this.step3Data.isGstRegistered) return true;
    return this.isGstinValid() && this.isGstLegalNameValid() && this.isGstStateValid();
  }

  // --- Subsection 4: Property Manager Details Validation ---

  isPropertyManagerNameValid(): boolean {
    return !!this.step3Data.propertyManagerName &&
           this.step3Data.propertyManagerName.trim().length > 0 &&
           this.step3Data.propertyManagerName.trim().length <= 255;
  }

  isPropertyManagerDesignationValid(): boolean {
    return !!this.step3Data.propertyManagerDesignation && this.step3Data.propertyManagerDesignation.trim().length > 0;
  }

  isPropertyManagerContactNumberValid(): boolean {
    if (!this.step3Data.propertyManagerContactNumber || !this.step3Data.propertyManagerContactNumber.trim()) return false;
    const phone = this.step3Data.propertyManagerContactNumber.trim();
    return /^[+]?[0-9\s\-()]{7,20}$/.test(phone);
  }

  isPropertyManagerEmailValid(): boolean {
    if (!this.step3Data.propertyManagerEmail || !this.step3Data.propertyManagerEmail.trim()) return false;
    const email = this.step3Data.propertyManagerEmail.trim();
    return /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(email);
  }

  isManagerSectionValid(): boolean {
    return this.isPropertyManagerNameValid() &&
           this.isPropertyManagerDesignationValid() &&
           this.isPropertyManagerContactNumberValid() &&
           this.isPropertyManagerEmailValid();
  }

  // --- Subsection 5: Operating Hours Validation ---

  isCheckInTimeValid(): boolean {
    return !!this.step3Data.checkInTime && this.step3Data.checkInTime.trim().length > 0;
  }

  isCheckOutTimeValid(): boolean {
    return !!this.step3Data.checkOutTime && this.step3Data.checkOutTime.trim().length > 0;
  }

  isOperatingHoursSectionValid(): boolean {
    if (!this.isCheckInTimeValid() || !this.isCheckOutTimeValid()) return false;
    if (this.step3Data.receptionAvailability === 'Limited Operating Hours') {
      return !!this.step3Data.receptionOpeningTime && this.step3Data.receptionOpeningTime.trim().length > 0 &&
             !!this.step3Data.receptionClosingTime && this.step3Data.receptionClosingTime.trim().length > 0;
    }
    return true;
  }

  isStep3FormValid(): boolean {
    return this.isContactSectionValid() &&
           this.isBusinessSectionValid() &&
           this.isGstSectionValid() &&
           this.isManagerSectionValid() &&
           this.isOperatingHoursSectionValid();
  }

  proceedToStep4(): void {
    this.step3Submitted = true;
    this.step3ErrorMessage = null;

    if (!this.isStep3FormValid()) {
      // Auto-expand the first invalid section and display clear error
      if (!this.isContactSectionValid()) {
        this.expandedStep3Section = 'contact';
        this.step3ErrorMessage = 'Please complete all required fields in Property Contact Details correctly.';
      } else if (!this.isBusinessSectionValid()) {
        this.expandedStep3Section = 'business';
        this.step3ErrorMessage = 'Please complete all required fields in Business Registration Details correctly.';
      } else if (!this.isGstSectionValid()) {
        this.expandedStep3Section = 'gst';
        this.step3ErrorMessage = 'Please provide a valid 15-character GSTIN, legal name, and state for GST registration.';
      } else if (!this.isManagerSectionValid()) {
        this.expandedStep3Section = 'manager';
        this.step3ErrorMessage = 'Please complete all required fields in Property Manager Details correctly.';
      } else if (!this.isOperatingHoursSectionValid()) {
        this.expandedStep3Section = 'operating';
        this.step3ErrorMessage = 'Please provide valid check-in, check-out and reception operating hours.';
      }
      return;
    }

    if (!this.step1Data.id) {
      this.step3ErrorMessage = 'Draft ID missing. Please return to Step 1 and save basic details first.';
      return;
    }

    this.step3Submitting = true;
    const payload: HotelStep3Request = {
      draftId: this.step1Data.id,
      propertyContactNumber: this.step3Data.propertyContactNumber.trim(),
      alternateContactNumber: this.step3Data.alternateContactNumber ? this.step3Data.alternateContactNumber.trim() : null,
      propertyEmail: this.step3Data.propertyEmail.trim(),
      websiteUrl: this.step3Data.websiteUrl ? this.step3Data.websiteUrl.trim() : null,
      registeredBusinessName: this.step3Data.registeredBusinessName.trim(),
      businessRegistrationType: this.step3Data.businessRegistrationType.trim(),
      otherBusinessRegistrationType: this.step3Data.businessRegistrationType === 'Other' && this.step3Data.otherBusinessRegistrationType ? this.step3Data.otherBusinessRegistrationType.trim() : null,
      businessRegistrationNumber: this.step3Data.businessRegistrationNumber.trim(),
      registeredBusinessAddress: this.step3Data.registeredBusinessAddress ? this.step3Data.registeredBusinessAddress.trim() : null,
      isGstRegistered: !!this.step3Data.isGstRegistered,
      gstin: this.step3Data.isGstRegistered && this.step3Data.gstin ? this.step3Data.gstin.trim().toUpperCase() : null,
      gstLegalName: this.step3Data.isGstRegistered && this.step3Data.gstLegalName ? this.step3Data.gstLegalName.trim() : null,
      gstState: this.step3Data.isGstRegistered && this.step3Data.gstState ? this.step3Data.gstState.trim() : null,
      propertyManagerName: this.step3Data.propertyManagerName.trim(),
      propertyManagerDesignation: this.step3Data.propertyManagerDesignation.trim(),
      propertyManagerContactNumber: this.step3Data.propertyManagerContactNumber.trim(),
      propertyManagerEmail: this.step3Data.propertyManagerEmail.trim(),
      checkInTime: this.step3Data.checkInTime.trim(),
      checkOutTime: this.step3Data.checkOutTime.trim(),
      receptionAvailability: this.step3Data.receptionAvailability.trim(),
      receptionOpeningTime: this.step3Data.receptionAvailability === 'Limited Operating Hours' && this.step3Data.receptionOpeningTime ? this.step3Data.receptionOpeningTime.trim() : null,
      receptionClosingTime: this.step3Data.receptionAvailability === 'Limited Operating Hours' && this.step3Data.receptionClosingTime ? this.step3Data.receptionClosingTime.trim() : null
    };

    this.hotelService.saveStep3Draft(this.step1Data.id, payload).subscribe({
      next: (res) => {
        this.step3Submitting = false;
        this.isStep3Dirty = false;
        if (this.editingFromReview) {
          this.editingFromReview = false;
          this.currentWizardStep = 5;
        } else {
          this.currentWizardStep = 4;
        }
      },
      error: (err) => {
        this.step3Submitting = false;
        console.error('Save Step 3 draft error:', err);
        this.step3ErrorMessage = err.error?.message || 'Failed to save property information. Please check your entries and try again.';
      }
    });
  }

  backToStep3(): void {
    this.currentWizardStep = 3;
  }

  // ==================== STEP 4 METHODS ====================
  toggleStep4Section(section: 'amenities' | 'policies' | 'rules'): void {
    if (this.expandedStep4Section === section) {
      this.expandedStep4Section = null;
    } else {
      this.expandedStep4Section = section;
    }
  }

  isStep4SectionExpanded(section: 'amenities' | 'policies' | 'rules'): boolean {
    return this.expandedStep4Section === section;
  }

  togglePredefinedAmenity(name: string): void {
    const idx = this.step4Data.predefinedAmenities.indexOf(name);
    if (idx >= 0) {
      this.step4Data.predefinedAmenities.splice(idx, 1);
    } else {
      this.step4Data.predefinedAmenities.push(name);
    }
    this.isStep4Dirty = true;
  }

  isPredefinedAmenitySelected(name: string): boolean {
    return this.step4Data.predefinedAmenities.includes(name);
  }

  openAddCustomAmenity(): void {
    this.showCustomAmenityInput = true;
    this.newCustomAmenityName = '';
    this.customAmenityError = '';
  }

  cancelAddCustomAmenity(): void {
    this.showCustomAmenityInput = false;
    this.newCustomAmenityName = '';
    this.customAmenityError = '';
  }

  submitAddCustomAmenity(): void {
    const trimmed = (this.newCustomAmenityName || '').trim();
    if (!trimmed) {
      this.customAmenityError = 'Please enter a valid amenity name.';
      return;
    }
    if (trimmed.length > 50) {
      this.customAmenityError = 'Amenity name must not exceed 50 characters.';
      return;
    }
    const isPredefined = this.predefinedAmenityCategories.some(cat =>
      cat.amenities.some(a => a.toLowerCase() === trimmed.toLowerCase())
    );
    if (isPredefined) {
      this.customAmenityError = 'This facility already exists in the standard categories above.';
      return;
    }
    const isCustomDup = this.step4Data.customAmenities.some(a => a.toLowerCase() === trimmed.toLowerCase());
    if (isCustomDup) {
      this.customAmenityError = 'This custom amenity has already been added.';
      return;
    }

    this.step4Data.customAmenities.push(trimmed);
    this.isStep4Dirty = true;
    this.cancelAddCustomAmenity();
  }

  removeCustomAmenity(name: string): void {
    this.step4Data.customAmenities = this.step4Data.customAmenities.filter(a => a !== name);
    this.isStep4Dirty = true;
  }

  toggleIdType(type: string): void {
    const idx = this.step4Data.acceptedIdentificationTypes.indexOf(type);
    if (idx >= 0) {
      this.step4Data.acceptedIdentificationTypes.splice(idx, 1);
      if (type === 'Other') {
        this.step4Data.customIdentificationType = '';
      }
    } else {
      this.step4Data.acceptedIdentificationTypes.push(type);
    }
    this.isStep4Dirty = true;
  }

  isIdTypeSelected(type: string): boolean {
    return this.step4Data.acceptedIdentificationTypes.includes(type);
  }

  isPoliciesSectionValid(): boolean {
    if (!this.step4Data.childrenPolicy || !this.step4Data.childrenPolicy.trim()) return false;
    if (!this.step4Data.petPolicy || !this.step4Data.petPolicy.trim()) return false;
    if (!this.step4Data.smokingPolicy || !this.step4Data.smokingPolicy.trim()) return false;
    if (!this.step4Data.extraBedPolicy || !this.step4Data.extraBedPolicy.trim()) return false;
    if (!this.step4Data.acceptedIdentificationTypes || this.step4Data.acceptedIdentificationTypes.length === 0) return false;
    if (this.step4Data.acceptedIdentificationTypes.includes('Other')) {
      if (!this.step4Data.customIdentificationType || !this.step4Data.customIdentificationType.trim()) return false;
      if (this.step4Data.customIdentificationType.trim().length > 150) return false;
    }
    return true;
  }

  isRulesSectionValid(): boolean {
    if (this.step4Data.additionalRules && this.step4Data.additionalRules.length > 1000) {
      return false;
    }
    return true;
  }

  isStep4FormValid(): boolean {
    return this.isPoliciesSectionValid() && this.isRulesSectionValid();
  }

  proceedToStep5(): void {
    this.step4Submitted = true;
    this.step4ErrorMessage = null;

    if (!this.isPoliciesSectionValid()) {
      this.expandedStep4Section = 'policies';
      if (!this.step4Data.acceptedIdentificationTypes || this.step4Data.acceptedIdentificationTypes.length === 0) {
        this.step4ErrorMessage = 'Please select at least one accepted identification document.';
      } else if (this.step4Data.acceptedIdentificationTypes.includes('Other') && (!this.step4Data.customIdentificationType || !this.step4Data.customIdentificationType.trim())) {
        this.step4ErrorMessage = 'Please specify the custom identification type when "Other" is selected.';
      } else {
        this.step4ErrorMessage = 'Please select a valid option for all required guest policies.';
      }
      return;
    }

    if (!this.isRulesSectionValid()) {
      this.expandedStep4Section = 'rules';
      this.step4ErrorMessage = 'Additional property rules must not exceed 1000 characters.';
      return;
    }

    if (!this.step1Data.id) {
      this.step4ErrorMessage = 'Draft ID missing. Please return to Step 1 and save basic details first.';
      return;
    }

    this.step4Submitting = true;
    const payload: HotelStep4Request = {
      draftId: this.step1Data.id,
      predefinedAmenities: [...this.step4Data.predefinedAmenities],
      customAmenities: [...this.step4Data.customAmenities],
      childrenPolicy: this.step4Data.childrenPolicy,
      petPolicy: this.step4Data.petPolicy,
      smokingPolicy: this.step4Data.smokingPolicy,
      extraBedPolicy: this.step4Data.extraBedPolicy,
      acceptedIdentificationTypes: [...this.step4Data.acceptedIdentificationTypes],
      customIdentificationType: this.step4Data.acceptedIdentificationTypes.includes('Other') && this.step4Data.customIdentificationType
        ? this.step4Data.customIdentificationType.trim() : null,
      additionalRules: this.step4Data.additionalRules && this.step4Data.additionalRules.trim() ? this.step4Data.additionalRules.trim() : null
    };

    this.hotelService.saveStep4Draft(this.step1Data.id, payload).subscribe({
      next: (res) => {
        this.step4Submitting = false;
        this.isStep4Dirty = false;
        this.editingFromReview = false;
        this.currentWizardStep = 5;
      },
      error: (err) => {
        this.step4Submitting = false;
        console.error('Save Step 4 draft error:', err);
        this.step4ErrorMessage = err.error?.message || 'Failed to save facilities and policies. Please check your entries and try again.';
      }
    });
  }

  backToStep4(): void {
    this.currentWizardStep = 4;
  }


  // =========================================================================
  // WP-05 STEP 5: REVIEW & SUBMIT METHODS
  // =========================================================================

  toggleReviewSection(section: 'basic' | 'address' | 'propertyInfo' | 'facilities', event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.reviewSectionsExpanded[section] = !this.reviewSectionsExpanded[section];
  }

  isReviewSectionExpanded(section: 'basic' | 'address' | 'propertyInfo' | 'facilities'): boolean {
    return !!this.reviewSectionsExpanded[section];
  }

  editReviewStep(step: number, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.editingFromReview = true;
    this.currentWizardStep = step;
  }

  saveAndReturnToReview(step: number): void {
    this.editingFromReview = true;
    if (step === 1) {
      this.proceedToStep2();
    } else if (step === 2) {
      this.proceedToStep3();
    } else if (step === 3) {
      this.proceedToStep4();
    } else if (step === 4) {
      this.proceedToStep5();
    }
  }

  openTermsModal(event?: Event): void {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    this.showTermsModal = true;
  }

  closeTermsModal(): void {
    this.showTermsModal = false;
  }

  submitFinalRegistration(): void {
    this.step5Submitted = true;
    this.step5ErrorMessage = null;

    if (!this.accuracyConfirmed) {
      this.step5ErrorMessage = 'You must confirm that the information provided is accurate and complete and agree to comply with TravelEasy\'s Partner Terms & Conditions.';
      return;
    }

    if (!this.step1Data.id) {
      this.step5ErrorMessage = 'Draft ID missing. Please return to Step 1 and complete required property details.';
      return;
    }

    this.step5Submitting = true;
    const submitPayload: HotelStep5SubmitRequest = {
      draftId: this.step1Data.id,
      confirmationAccepted: true
    };

    this.hotelService.submitPropertyRegistration(this.step1Data.id, submitPayload).subscribe({
      next: (res: HotelStep5Response) => {
        this.step5Submitting = false;
        this.registrationSuccess = true;
        this.activatedPropertyDetails = res;
      },
      error: (err) => {
        this.step5Submitting = false;
        console.error('Final property submission error:', err);
        this.step5ErrorMessage = err.error?.message || 'Failed to submit property registration. Please verify all steps and try again.';
      }
    });
  }

  viewActivatedProperty(): void {
    this.propertyViewMode = 'list';
    this.registrationSuccess = false;
    this.activatedPropertyDetails = null;
    this.loadInitialData();
  }

  getReviewClassification(): string {
    const classification = this.step1Data.propertyClassification;
    if (classification === 'ONE_STAR') return '1 Star';
    if (classification === 'TWO_STAR') return '2 Star';
    if (classification === 'THREE_STAR') return '3 Star';
    if (classification === 'FOUR_STAR') return '4 Star';
    if (classification === 'FIVE_STAR') return '5 Star';
    if (classification === 'FIVE_STAR_DELUXE') return '5 Star Deluxe';
    if (classification === 'OTHER') {
      return this.step1Data.customClassification && this.step1Data.customClassification.trim()
        ? this.step1Data.customClassification.trim()
        : 'Other recognized classification';
    }
    if (classification === 'NOT_CLASSIFIED' || classification === 'NOT_OFFICIALLY_CLASSIFIED') {
      return 'Not officially classified';
    }
    return this.step1Data.propertyClassification || 'Not officially classified';
  }

  getReviewStarRating(): string {
    return this.getReviewClassification();
  }

  getReviewPropertySegment(): string {
    return this.step1Data.propertySegment || 'Standard';
  }

  getReviewCoverImageUrl(): string {
    const cover = this.wizardImages.find(img => img !== null && img.isCover && (img.previewUrl || img.imageUrl));
    if (cover) {
      return cover.previewUrl || cover.imageUrl || 'https://images.unsplash.com/photo-1566073771259-6a8506099945';
    }
    const anyImg = this.wizardImages.find(img => img !== null && (img.previewUrl || img.imageUrl));
    if (anyImg) {
      return anyImg.previewUrl || anyImg.imageUrl || 'https://images.unsplash.com/photo-1566073771259-6a8506099945';
    }
    return 'https://images.unsplash.com/photo-1566073771259-6a8506099945';
  }

  getFormattedFullAddress(): string {
    const parts = [];
    if (this.step2Data.addressLine1) parts.push(this.step2Data.addressLine1.trim());
    if (this.step2Data.addressLine2) parts.push(this.step2Data.addressLine2.trim());
    if (this.step2Data.landmark) parts.push(`Near ${this.step2Data.landmark.trim()}`);
    if (this.step2Data.city) parts.push(this.step2Data.city.trim());
    let statePin = '';
    if (this.step2Data.state) statePin += this.step2Data.state.trim();
    if (this.step2Data.postalCode) statePin += ` - ${this.step2Data.postalCode.trim()}`;
    if (statePin) parts.push(statePin);
    if (this.step2Data.country) parts.push(this.step2Data.country.trim());
    return parts.join(', ');
  }

  formatTime12Hour(timeStr?: string): string {
    if (!timeStr) return '--:--';
    const parts = timeStr.split(':');
    if (parts.length < 2) return timeStr;
    let hour = parseInt(parts[0], 10);
    const min = parts[1];
    const ampm = hour >= 12 ? 'PM' : 'AM';
    hour = hour % 12;
    if (hour === 0) hour = 12;
    const hourStr = hour < 10 ? `0${hour}` : `${hour}`;
    return `${hourStr}:${min} ${ampm}`;
  }

  getAllSelectedAmenities(): string[] {
    return [...this.step4Data.predefinedAmenities, ...this.step4Data.customAmenities];
  }

  getAmenityIcon(amenity: string): string {
    const lower = amenity.toLowerCase();
    if (lower.includes('wifi') || lower.includes('wi-fi')) return 'fa-solid fa-wifi';
    if (lower.includes('restaurant')) return 'fa-solid fa-utensils';
    if (lower.includes('room service')) return 'fa-solid fa-bell-concierge';
    if (lower.includes('breakfast')) return 'fa-solid fa-mug-saucer';
    if (lower.includes('parking')) return 'fa-solid fa-square-parking';
    if (lower.includes('shuttle')) return 'fa-solid fa-van-shuttle';
    if (lower.includes('pool')) return 'fa-solid fa-person-swimming';
    if (lower.includes('gym')) return 'fa-solid fa-dumbbell';
    if (lower.includes('spa')) return 'fa-solid fa-spa';
    if (lower.includes('cctv')) return 'fa-solid fa-video';
    if (lower.includes('security')) return 'fa-solid fa-shield-halved';
    if (lower.includes('fire')) return 'fa-solid fa-fire-extinguisher';
    if (lower.includes('accessible') || lower.includes('entrance')) return 'fa-solid fa-wheelchair';
    if (lower.includes('elevator') || lower.includes('lift')) return 'fa-solid fa-elevator';
    if (lower.includes('laundry')) return 'fa-solid fa-soap';
    if (lower.includes('reception')) return 'fa-solid fa-bell-concierge';
    if (lower.includes('luggage')) return 'fa-solid fa-suitcase';
    if (lower.includes('business')) return 'fa-solid fa-briefcase';
    return 'fa-solid fa-check';
  }

  getFormattedRulesList(): string[] {
    if (!this.step4Data.additionalRules || !this.step4Data.additionalRules.trim()) {
      return [
        'Outside food and beverages are not allowed.',
        'Maintain quiet hours from 10:00 PM to 6:00 AM.',
        'Valid government-issued ID is required for all guests.',
        'Visitors are allowed only till 9:00 PM.'
      ];
    }
    return this.step4Data.additionalRules
      .split('\n')
      .map(line => line.trim())
      .filter(line => line.length > 0)
      .map(line => line.startsWith('-') || line.startsWith('•') ? line.replace(/^[-•]\s*/, '') : line);
  }

  getFormattedIdentification(): string {
    const types = [...this.step4Data.acceptedIdentificationTypes];
    const otherIdx = types.indexOf('Other');
    if (otherIdx !== -1) {
      types.splice(otherIdx, 1);
      if (this.step4Data.customIdentificationType && this.step4Data.customIdentificationType.trim()) {
        types.push(this.step4Data.customIdentificationType.trim());
      }
    }
    return types.length > 0 ? types.join(', ') : 'None specified';
  }

}
