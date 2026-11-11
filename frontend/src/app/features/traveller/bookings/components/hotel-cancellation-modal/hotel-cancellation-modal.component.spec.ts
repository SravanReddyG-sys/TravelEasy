import { SimpleChange } from '@angular/core';
import { of, throwError } from 'rxjs';
import { HotelCancellationModalComponent } from './hotel-cancellation-modal.component';
import { HotelBookingCardViewModel } from '../../models/hotel-booking-view.model';
import { buildHotelBookingCardViewModel } from '../../utils/hotel-booking-presenter.util';
import {
  HotelCancellationQuoteResponse,
  HotelCancellationResponse
} from '../../models/hotel-cancellation.model';

function createCard(overrides: Partial<HotelBookingCardViewModel> = {}): HotelBookingCardViewModel {
  const built = buildHotelBookingCardViewModel({
    id: 901,
    bookingRef: 'HOTEL-DKG-20261111-CA74',
    serviceType: 'HOTEL',
    serviceId: 42,
    serviceTitle: 'GSR Grand Premium (Deluxe Room)',
    totalAmount: 2499,
    status: 'CONFIRMED',
    bookingDate: '2026-11-11T10:24:00',
    detailsJson: JSON.stringify({
      checkIn: '2026-11-11',
      checkOut: '2026-11-12',
      rooms: 1,
      adults: 2,
      children: 0
    })
  });
  return { ...built, ...overrides };
}

function createQuote(
  overrides: Partial<HotelCancellationQuoteResponse> = {}
): HotelCancellationQuoteResponse {
  return {
    bookingId: 901,
    bookingRef: 'HOTEL-DKG-20261111-CA74',
    serviceType: 'HOTEL',
    currentStatus: 'CONFIRMED',
    cancellable: true,
    alreadyCancelled: false,
    policyTier: 'PARTIAL_REFUND_50_24H_48H',
    totalAmount: 2499,
    refundPercentage: 50,
    refundAmount: 1249,
    cancellationFee: 1250,
    eligibleForRefund: true,
    description: 'Cancelling 24-48h before check-in gives 50% refund.',
    checkInDate: '2026-11-11',
    checkOutDate: '2026-11-12',
    scheduledCheckInTime: '2026-11-11T14:00:00+05:30',
    evaluationTime: '2026-11-10T10:00:00+05:30',
    policyTimeZone: 'Asia/Kolkata',
    hoursUntilCheckIn: 28,
    ...overrides
  };
}

function createCancelResponse(
  overrides: Partial<HotelCancellationResponse> = {}
): HotelCancellationResponse {
  return {
    message: 'Hotel booking cancelled successfully.',
    bookingId: 901,
    bookingRef: 'HOTEL-DKG-20261111-CA74',
    serviceType: 'HOTEL',
    status: 'CANCELLED',
    idempotent: false,
    previousStatus: 'CONFIRMED',
    cancelledAt: '2026-11-11T11:02:00',
    cancellationReason: 'Change in travel plans',
    refundAmount: 1249,
    cancellationFee: 1250,
    cancellationPolicyTier: 'PARTIAL_REFUND_50_24H_48H',
    inventoryReleaseStatus: 'RELEASED',
    refundStatus: 'INITIATED',
    refundId: 'rfnd_901',
    refundAttemptKey: 'attempt_901',
    ...overrides
  };
}

function openComponent(
  mockBookingService: any,
  card: HotelBookingCardViewModel = createCard()
): HotelCancellationModalComponent {
  const component = new HotelCancellationModalComponent(mockBookingService);
  component.bookingCard = card;
  component.showModal = true;
  component.ngOnChanges({
    showModal: new SimpleChange(false, true, true),
    bookingCard: new SimpleChange(null, card, true)
  });
  return component;
}

describe('HotelCancellationModalComponent', () => {
  it('loads authoritative Step 1 quote by booking ID and walks through Steps 1 -> 2 -> 3', () => {
    let cancelPayloadReceived: any = null;
    const mockService = {
      getHotelCancellationQuoteById: (id: number) => of(createQuote({ bookingId: id })),
      getHotelCancellationQuoteByRef: () => {
        throw new Error('Should call ById when id > 0');
      },
      cancelHotelBookingById: (_id: number, body: any) => {
        cancelPayloadReceived = body;
        return of(createCancelResponse());
      }
    };

    const component = openComponent(mockService);
    const updatedEvents: any[] = [];
    component.bookingUpdated.subscribe(evt => updatedEvents.push(evt));

    expect(component.currentStep).toBe(1);
    expect(component.formattedRefundAmount).toBe('₹1,249');
    expect(component.formattedCancellationFee).toBe('₹1,250');
    expect(component.formattedRefundPercentage).toBe('50%');

    component.proceedToConfirmStep();
    expect(component.currentStep).toBe(2);

    component.selectedReasonOption = 'Change in travel plans';
    component.submitCancellation();

    expect(cancelPayloadReceived).toEqual({ reason: 'Change in travel plans' });
    expect(component.currentStep).toBe(3);
    expect(component.result?.headline).toBe('Booking Cancelled Successfully');
    expect(component.result?.refund.badgeLabel).toBe('Refund Initiated');
    expect(component.result?.inventory.label).toBe('Released');
    expect(updatedEvents.length).toBe(1);
  });

  it('uses ref-based isolated hotel endpoints when numeric id is missing and omits blank reason', () => {
    let refQuoteCalled = false;
    let cancelPayloadReceived: any = null;
    const mockService = {
      getHotelCancellationQuoteById: () => {
        throw new Error('Should fall back to ByRef when id is null');
      },
      getHotelCancellationQuoteByRef: (ref: string) => {
        refQuoteCalled = true;
        return of(createQuote({ bookingRef: ref, policyTier: 'FREE_CANCELLATION_48H', refundPercentage: 100, refundAmount: 2499, cancellationFee: 0 }));
      },
      cancelHotelBookingByRef: (_ref: string, body: any) => {
        cancelPayloadReceived = body;
        return of(createCancelResponse({ refundStatus: 'PROCESSED', refundAmount: 2499, cancellationFee: 0 }));
      }
    };

    const component = openComponent(mockService, createCard({ id: undefined }));
    expect(refQuoteCalled).toBe(true);
    expect(component.policyTierDisplay?.tone).toBe('full');

    component.proceedToConfirmStep();
    component.selectedReasonOption = '';
    component.customReasonText = '   ';
    component.submitCancellation();

    expect(cancelPayloadReceived).toEqual({});
    expect(component.currentStep).toBe(3);
    expect(component.result?.refund.badgeLabel).toBe('Refund Processed');
  });

  it('prevents Step 2 transition when quote reports cancellable=false or alreadyCancelled=true', () => {
    const mockService = {
      getHotelCancellationQuoteById: () =>
        of(createQuote({ cancellable: false, alreadyCancelled: false, description: 'Guest is already checked in.' }))
    };
    const component = openComponent(mockService);
    expect(component.currentStep).toBe(1);
    component.proceedToConfirmStep();
    expect(component.currentStep).toBe(1);
    expect(component.canSubmitCancellation).toBe(false);
  });

  it('allows zero-refund cancellation when booking is still CONFIRMED inside <24h window', () => {
    const mockService = {
      getHotelCancellationQuoteById: () =>
        of(
          createQuote({
            cancellable: true,
            eligibleForRefund: false,
            policyTier: 'NON_REFUNDABLE_LESS_THAN_24H',
            refundPercentage: 0,
            refundAmount: 0,
            cancellationFee: 2499
          })
        ),
      cancelHotelBookingById: () =>
        of(
          createCancelResponse({
            refundAmount: 0,
            cancellationFee: 2499,
            refundStatus: 'NON_REFUNDABLE',
            inventoryReleaseStatus: 'RELEASED'
          })
        )
    };

    const component = openComponent(mockService);
    component.proceedToConfirmStep();
    expect(component.currentStep).toBe(2);
    component.submitCancellation();

    expect(component.currentStep).toBe(3);
    expect(component.result?.formattedRefundAmount).toBe('₹0');
    expect(component.result?.refund.badgeLabel).toBe('No Refund Applicable');
    expect(component.result?.inventory.label).toBe('Released');
  });

  it('reconciles uncertain TimeoutError with a single GET probe without resending PUT', () => {
    let putCalls = 0;
    let getByIdCalls = 0;
    const mockService = {
      getHotelCancellationQuoteById: () => of(createQuote()),
      cancelHotelBookingById: () => {
        putCalls += 1;
        return throwError(() => ({ name: 'TimeoutError' }));
      },
      getBookingById: () => {
        getByIdCalls += 1;
        return of({
          id: 901,
          bookingRef: 'HOTEL-DKG-20261111-CA74',
          status: 'CANCELLED',
          refundStatus: 'INITIATED',
          refundAmount: 1249,
          cancellationFee: 1250,
          cancellationPolicyTier: 'PARTIAL_REFUND_50_24H_48H',
          inventoryReleaseStatus: 'FAILED_RETRYABLE',
          cancelledAt: '2026-11-11T11:02:00'
        });
      }
    };

    const component = openComponent(mockService);
    component.proceedToConfirmStep();
    component.submitCancellation();

    expect(putCalls).toBe(1);
    expect(getByIdCalls).toBe(1);
    expect(component.currentStep).toBe(3);
    expect(component.result?.reconciledFromServerProbe).toBe(true);
    expect(component.result?.inventory.label).toBe('Release Retrying');
  });

  it('stays safely on Step 2 when timeout GET probe confirms booking is still CONFIRMED', () => {
    let refreshQuoteCalls = 0;
    const mockService = {
      getHotelCancellationQuoteById: () => {
        refreshQuoteCalls += 1;
        return of(createQuote());
      },
      cancelHotelBookingById: () => throwError(() => ({ status: 504 })),
      getBookingById: () =>
        of({
          id: 901,
          bookingRef: 'HOTEL-DKG-20261111-CA74',
          status: 'CONFIRMED'
        })
    };

    const component = openComponent(mockService);
    component.proceedToConfirmStep();
    component.submitCancellation();

    expect(component.currentStep).toBe(2);
    expect(component.submitError?.message).toContain('booking is still confirmed');
    expect(refreshQuoteCalls).toBe(2);
  });

  it('emits refreshBookings and blocks duplicate submission on HTTP 400 status-changed errors', () => {
    let refreshedCount = 0;
    const mockService = {
      getHotelCancellationQuoteById: () => of(createQuote()),
      cancelHotelBookingById: () =>
        throwError(() => ({
          status: 400,
          error: { message: 'Checked-in bookings cannot be cancelled.' }
        }))
    };

    const component = openComponent(mockService);
    component.refreshBookings.subscribe(() => {
      refreshedCount += 1;
    });
    component.proceedToConfirmStep();
    component.submitCancellation();

    expect(component.currentStep).toBe(2);
    expect(component.submitError?.isStatusChanged).toBe(true);
    expect(component.submitError?.message).toBe('Checked-in bookings cannot be cancelled.');
    expect(component.canSubmitCancellation).toBe(false);
    expect(refreshedCount).toBe(1);
  });
});
