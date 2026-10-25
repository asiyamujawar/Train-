package com.trainconcierge.hotel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trainconcierge.dto.HotelRequest;
import com.trainconcierge.dto.HotelResponse;
import com.trainconcierge.dto.JourneyLegRequest;
import com.trainconcierge.dto.JourneyRequest;
import com.trainconcierge.entity.DisruptionEvent;
import com.trainconcierge.entity.DisruptionType;
import com.trainconcierge.entity.EventState;
import com.trainconcierge.entity.HotelAvailabilityStatus;
import com.trainconcierge.entity.HotelDecisionOutcome;
import com.trainconcierge.entity.HotelRecommendation;
import com.trainconcierge.entity.ImpactSeverity;
import com.trainconcierge.entity.ImpactState;
import com.trainconcierge.entity.ImpactType;
import com.trainconcierge.entity.JourneyImpact;
import com.trainconcierge.entity.JourneyLeg;
import com.trainconcierge.entity.Passenger;
import com.trainconcierge.entity.RecommendationStatus;
import com.trainconcierge.entity.Station;
import com.trainconcierge.entity.Train;
import com.trainconcierge.entity.TrainRecommendation;
import com.trainconcierge.entity.TrainRunningStatus;
import com.trainconcierge.entity.TrainStop;
import com.trainconcierge.repository.DisruptionEventRepository;
import com.trainconcierge.repository.HotelRecommendationRepository;
import com.trainconcierge.repository.HotelRepository;
import com.trainconcierge.repository.JourneyImpactRepository;
import com.trainconcierge.repository.JourneyLegRepository;
import com.trainconcierge.repository.JourneyRepository;
import com.trainconcierge.repository.PassengerRepository;
import com.trainconcierge.repository.StationRepository;
import com.trainconcierge.repository.TrainRecommendationRepository;
import com.trainconcierge.repository.TrainRepository;
import com.trainconcierge.repository.TrainStopRepository;
import com.trainconcierge.seed.HotelDemoDataSeeder;
import com.trainconcierge.service.HotelService;
import com.trainconcierge.service.JourneyService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 7 integration tests.
 *
 * <p>They run against the real MySQL schema created by Flyway (V1-V9) and reuse
 * the Phase 1-6 models: master data -&gt; journey -&gt; journey impact -&gt;
 * (optional) alternative-train recommendation -&gt; hotel recommendation. The
 * scheduled monitor is disabled, every test rolls back and no external service,
 * booking system or hotel API is contacted.</p>
 *
 * <p>Impacts are created directly so each decision rule can be exercised in
 * isolation and deterministically; the full Phase 4/5/6 pipeline is covered by
 * the earlier phases' tests and by the Postman workflow in {@code phase7.md}.</p>
 *
 * <p>Returned hotel suggestions are illustrative only: the tests assert that the
 * system never claims real availability ("not a booking").</p>
 */
@SpringBootTest(properties = {
        "trainconcierge.monitoring.enabled=false",
        "trainconcierge.hotel.overnight-delay-threshold-minutes=480",
        "trainconcierge.hotel.max-recommendations=5"
})
@AutoConfigureMockMvc
@Transactional
class HotelApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TrainStopRepository trainStopRepository;

    @Autowired
    private PassengerRepository passengerRepository;

    @Autowired
    private JourneyRepository journeyRepository;

    @Autowired
    private JourneyLegRepository journeyLegRepository;

    @Autowired
    private JourneyImpactRepository journeyImpactRepository;

    @Autowired
    private DisruptionEventRepository disruptionEventRepository;

    @Autowired
    private TrainRecommendationRepository trainRecommendationRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private HotelRecommendationRepository hotelRecommendationRepository;

    @Autowired
    private HotelService hotelService;

    @Autowired
    private HotelDemoDataSeeder hotelDemoDataSeeder;

    @Autowired
    private JourneyService journeyService;

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final LocalDate journeyDate = LocalDate.now().plusDays(2);

    // ------------------------------------------------------------------
    // 1. Cancellation without a feasible same-day alternative -> overnight
    // ------------------------------------------------------------------
    @Test
    @DisplayName("a cancelled leg with no feasible alternative suggests an overnight stay at the origin station")
    void cancellationWithoutAlternativeSuggestsOvernightStay() throws Exception {
        Station origin = newStation("AA");
        Station destination = newStation("AB");
        long trainId = newTrain("930001", origin, destination);
        addStop(trainId, origin, 1, null, "08:00:00", 0);
        addStop(trainId, destination, 2, "10:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(trainId, origin.getId(), destination.getId()));
        JourneyLeg leg = legs(journeyId).get(0);
        impact(journeyId, leg, ImpactType.CANCELLATION, ImpactSeverity.CRITICAL, 0);

        HotelResponse nearOrigin = createHotel("Demo Origin Inn (fictional)", origin, "1.00", "2500.00", "4.0");
        // A hotel near the *destination* must not be suggested: the passenger never left the origin.
        createHotel("Demo Destination Inn (fictional)", destination, "0.50", "1500.00", "4.5");

        mockMvc.perform(get("/api/v1/journeys/{journeyId}/hotel-recommendations", journeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionOutcome").value("OVERNIGHT_STAY_LIKELY"))
                .andExpect(jsonPath("$.overnightStayLikely").value(true))
                .andExpect(jsonPath("$.relevantStation.id").value(origin.getId()))
                .andExpect(jsonPath("$.recommendations", hasSize(1)))
                .andExpect(jsonPath("$.recommendations[0].hotel.id").value(nearOrigin.id()))
                .andExpect(jsonPath("$.recommendations[0].hotel.availabilityStatus").value("AVAILABILITY_UNKNOWN"))
                .andExpect(jsonPath("$.recommendations[0].reason", containsString("not a booking")))
                .andExpect(jsonPath("$.recommendations[0].reason", containsString("per night")))
                .andExpect(jsonPath("$.recommendations[0].reason", containsString("km from the station")));
    }

    // ------------------------------------------------------------------
    // 2. Cancellation with a feasible alternative -> same-day travel
    // ------------------------------------------------------------------
    @Test
    @DisplayName("a cancelled leg with a feasible alternative-train recommendation stays same-day")
    void cancellationWithFeasibleAlternativeIsSameDay() throws Exception {
        Station origin = newStation("BA");
        Station destination = newStation("BB");
        long trainId = newTrain("930002", origin, destination);
        addStop(trainId, origin, 1, null, "08:00:00", 0);
        addStop(trainId, destination, 2, "10:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(trainId, origin.getId(), destination.getId()));
        JourneyLeg leg = legs(journeyId).get(0);
        JourneyImpact cancellation = impact(journeyId, leg, ImpactType.CANCELLATION, ImpactSeverity.CRITICAL, 0);
        createHotel("Demo Origin Inn (fictional)", origin, "1.00", "2500.00", "4.0");

        // Phase 6 has produced an AVAILABLE alternative for this exact impact.
        addAvailableAlternative(journeyId, leg, cancellation, trainId);

        mockMvc.perform(get("/api/v1/journeys/{journeyId}/hotel-recommendations", journeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionOutcome").value("SAME_DAY_TRAVEL_FEASIBLE"))
                .andExpect(jsonPath("$.overnightStayLikely").value(false))
                .andExpect(jsonPath("$.recommendations", hasSize(0)));
    }

    // ------------------------------------------------------------------
    // 3. Missed connection -> overnight at the transfer station
    // ------------------------------------------------------------------
    @Test
    @DisplayName("a missed connection makes an overnight stay likely at the transfer station")
    void missedConnectionMakesOvernightLikely() throws Exception {
        Station s1 = newStation("CA");
        Station s2 = newStation("CB");
        Station s3 = newStation("CC");
        long leg1Train = newTrain("930003", s1, s2);
        addStop(leg1Train, s1, 1, null, "08:00:00", 0);
        addStop(leg1Train, s2, 2, "10:00:00", "10:10:00", 0);
        long leg2Train = newTrain("930004", s2, s3);
        addStop(leg2Train, s2, 1, null, "16:00:00", 0);
        addStop(leg2Train, s3, 2, "18:00:00", null, 0);

        long journeyId = createJourney(journeyDate,
                new LegSpec(leg1Train, s1.getId(), s2.getId()),
                new LegSpec(leg2Train, s2.getId(), s3.getId()));
        JourneyLeg firstLeg = legs(journeyId).get(0);
        impact(journeyId, firstLeg, ImpactType.MISSED_CONNECTION, ImpactSeverity.CRITICAL, 90);

        HotelResponse transferHotel = createHotel("Demo Transfer Inn (fictional)", s2, "0.90", "1800.00", "3.8");

        // The passenger is stranded where the connection was missed: the first leg's destination.
        mockMvc.perform(get("/api/v1/journeys/{journeyId}/hotel-recommendations", journeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionOutcome").value("OVERNIGHT_STAY_LIKELY"))
                .andExpect(jsonPath("$.relevantStation.id").value(s2.getId()))
                .andExpect(jsonPath("$.recommendations", hasSize(1)))
                .andExpect(jsonPath("$.recommendations[0].hotel.id").value(transferHotel.id()));
    }

    // ------------------------------------------------------------------
    // 4. Ordinary delay -> no hotel
    // ------------------------------------------------------------------
    @Test
    @DisplayName("an ordinary delay does not require an overnight stay")
    void ordinaryDelayDoesNotRequireHotel() {
        Station s1 = newStation("DA");
        Station s2 = newStation("DB");
        long trainId = newTrain("930005", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(trainId, s1.getId(), s2.getId()));
        JourneyLeg leg = legs(journeyId).get(0);
        impact(journeyId, leg, ImpactType.DELAY, ImpactSeverity.MEDIUM, 60);
        createHotel("Demo Origin Inn (fictional)", s1, "1.00", "2500.00", "4.0");

        assertThat(performGetOutcome(journeyId)).isEqualTo(HotelDecisionOutcome.SAME_DAY_TRAVEL_FEASIBLE);
    }

    // ------------------------------------------------------------------
    // 5. Delay that crosses midnight -> overnight
    // ------------------------------------------------------------------
    @Test
    @DisplayName("a delay that pushes arrival past midnight makes an overnight stay likely")
    void delayCrossingMidnightForcesOvernight() {
        Station s1 = newStation("EA");
        Station s2 = newStation("EB");
        long trainId = newTrain("930006", s1, s2);
        addStop(trainId, s1, 1, null, "21:00:00", 0);
        addStop(trainId, s2, 2, "23:30:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(trainId, s1.getId(), s2.getId()));
        JourneyLeg leg = legs(journeyId).get(0);
        impact(journeyId, leg, ImpactType.DELAY, ImpactSeverity.HIGH, 60);
        createHotel("Demo Arrival Inn (fictional)", s2, "0.70", "2200.00", "4.1");

        assertThat(performGetOutcome(journeyId)).isEqualTo(HotelDecisionOutcome.OVERNIGHT_STAY_LIKELY);
    }

    // ------------------------------------------------------------------
    // 6. No active impact -> same-day travel
    // ------------------------------------------------------------------
    @Test
    @DisplayName("a journey with no active impact is reported as same-day feasible")
    void noActiveImpactIsSameDayFeasible() throws Exception {
        Station s1 = newStation("FA");
        Station s2 = newStation("FB");
        long trainId = newTrain("930007", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(trainId, s1.getId(), s2.getId()));

        mockMvc.perform(get("/api/v1/journeys/{journeyId}/hotel-recommendations", journeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionOutcome").value("SAME_DAY_TRAVEL_FEASIBLE"))
                .andExpect(jsonPath("$.relevantStation").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.recommendations", hasSize(0)));
    }

    // ------------------------------------------------------------------
    // 7. Journey-level impact -> insufficient travel data
    // ------------------------------------------------------------------
    @Test
    @DisplayName("a journey-level impact yields INSUFFICIENT_TRAVEL_DATA because no station is affected")
    void journeyLevelImpactIsInsufficientTravelData() {
        Station s1 = newStation("GA");
        Station s2 = newStation("GB");
        long trainId = newTrain("930008", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(trainId, s1.getId(), s2.getId()));
        impact(journeyId, null, ImpactType.ITINERARY_DISRUPTED, ImpactSeverity.HIGH, null);

        assertThat(performGetOutcome(journeyId)).isEqualTo(HotelDecisionOutcome.INSUFFICIENT_TRAVEL_DATA);
    }

    // ------------------------------------------------------------------
    // 8. Insufficient timetable data -> explicit outcome
    // ------------------------------------------------------------------
    @Test
    @DisplayName("a delay impact with no recorded delay minutes yields INSUFFICIENT_TRAVEL_DATA")
    void missingDelayMinutesIsInsufficientTravelData() throws Exception {
        Station s1 = newStation("HA");
        Station s2 = newStation("HB");
        long trainId = newTrain("930009", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(trainId, s1.getId(), s2.getId()));
        JourneyLeg leg = legs(journeyId).get(0);
        impact(journeyId, leg, ImpactType.DELAY, ImpactSeverity.LOW, null);
        createHotel("Demo Origin Inn (fictional)", s1, "1.00", "2500.00", "4.0");

        mockMvc.perform(get("/api/v1/journeys/{journeyId}/hotel-recommendations", journeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionOutcome").value("INSUFFICIENT_TRAVEL_DATA"))
                .andExpect(jsonPath("$.recommendations", hasSize(0)))
                .andExpect(jsonPath("$.decisionReason").isNotEmpty());
    }

    // ------------------------------------------------------------------
    // 9. Hotel search filters (missing optional data is never hidden)
    // ------------------------------------------------------------------
    @Test
    @DisplayName("hotel search filters work and never hide hotels with unknown optional data")
    void hotelSearchFilters() throws Exception {
        Station station = newStation("IA");
        HotelResponse cheapNear = createHotel("Demo Cheap Near (fictional)", station, "0.50", "1000.00", "3.0");
        HotelResponse priceyFar = createHotel("Demo Pricey Far (fictional)", station, "9.00", "9000.00", "4.8");
        // This hotel deliberately has no price, distance, rating, currency or amenities.
        HotelResponse unknown = createHotel("Demo Unknown Data (fictional)", station,
                null, null, null, null, null, true);
        createHotel("Demo Closed (fictional)", station, "1.00", "1500.00", "4.2", "INR", "WIFI", false);

        // maxPrice: keeps the cheaper hotel *and* the hotel with an unknown price.
        mockMvc.perform(get("/api/v1/hotels").param("stationId", station.getId().toString())
                        .param("maxPrice", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name == 'Demo Cheap Near (fictional)')]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.name == 'Demo Unknown Data (fictional)')]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.name == 'Demo Pricey Far (fictional)')]", hasSize(0)));

        // maxDistanceKm 1 -> the far, pricey hotel is excluded.
        mockMvc.perform(get("/api/v1/hotels").param("stationId", station.getId().toString())
                        .param("maxDistanceKm", "1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name == 'Demo Pricey Far (fictional)')]", hasSize(0)))
                .andExpect(jsonPath("$.content[?(@.name == 'Demo Cheap Near (fictional)')]", hasSize(1)));

        // minRating 4.5 -> only the 4.8 hotel (the unknown rating is kept, the 3.0 is not).
        mockMvc.perform(get("/api/v1/hotels").param("stationId", station.getId().toString())
                        .param("minRating", "4.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name == 'Demo Pricey Far (fictional)')]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.name == 'Demo Cheap Near (fictional)')]", hasSize(0)))
                .andExpect(jsonPath("$.content[?(@.name == 'Demo Unknown Data (fictional)')]", hasSize(1)));

        // active filter: the deactivated hotel is only visible with active=false.
        mockMvc.perform(get("/api/v1/hotels").param("stationId", station.getId().toString())
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Demo Closed (fictional)"));

        // currency filter requires an actual match, so the unknown-currency hotel is excluded.
        mockMvc.perform(get("/api/v1/hotels").param("stationId", station.getId().toString())
                        .param("currency", "INR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name == 'Demo Unknown Data (fictional)')]", hasSize(0)))
                .andExpect(jsonPath("$.content[?(@.name == 'Demo Cheap Near (fictional)')]", hasSize(1)));

        // amenity filter.
        mockMvc.perform(get("/api/v1/hotels").param("stationId", station.getId().toString())
                        .param("amenity", "WIFI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name == 'Demo Closed (fictional)')]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.name == 'Demo Cheap Near (fictional)')]", hasSize(0)));

        // Verify computed response fields.
        assertThat(cheapNear.pricePerNight()).isEqualByComparingTo("1000.00");
        assertThat(priceyFar.rating()).isEqualByComparingTo("4.8");
        assertThat(unknown.pricePerNight()).isNull();
        assertThat(unknown.availabilityStatus()).isEqualTo(HotelAvailabilityStatus.AVAILABILITY_UNKNOWN);
    }

    // ------------------------------------------------------------------
    // 10. Inactive hotels are never recommended
    // ------------------------------------------------------------------
    @Test
    @DisplayName("an inactive hotel is never part of the recommendations")
    void inactiveHotelsAreNotRecommended() throws Exception {
        Station s1 = newStation("JA");
        Station s2 = newStation("JB");
        long trainId = newTrain("930010", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(trainId, s1.getId(), s2.getId()));
        JourneyLeg leg = legs(journeyId).get(0);
        impact(journeyId, leg, ImpactType.CANCELLATION, ImpactSeverity.CRITICAL, 0);

        HotelResponse active = createHotel("Demo Active Inn (fictional)", s1, "1.00", "2000.00", "4.0");
        createHotel("Demo Inactive Inn (fictional)", s1, "0.50", "1000.00", "4.9", "INR", "WIFI", false);

        mockMvc.perform(get("/api/v1/journeys/{journeyId}/hotel-recommendations", journeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations", hasSize(1)))
                .andExpect(jsonPath("$.recommendations[0].hotel.id").value(active.id()));
    }

    // ------------------------------------------------------------------
    // 11. Ranking is deterministic (distance, then price, then name)
    // ------------------------------------------------------------------
    @Test
    @DisplayName("hotels are ranked by distance, then price, then a stable name tie-breaker")
    void rankingIsDeterministic() throws Exception {
        Station s1 = newStation("KA");
        Station s2 = newStation("KB");
        long trainId = newTrain("930011", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(trainId, s1.getId(), s2.getId()));
        JourneyLeg leg = legs(journeyId).get(0);
        impact(journeyId, leg, ImpactType.CANCELLATION, ImpactSeverity.CRITICAL, 0);

        HotelResponse closest = createHotel("Demo Bravo (fictional)", s1, "0.50", "3000.00", "4.0");
        HotelResponse midCheap = createHotel("Demo Alpha (fictional)", s1, "1.00", "1000.00", "4.0");
        HotelResponse midPricey = createHotel("Demo Charly (fictional)", s1, "1.00", "2500.00", "4.9");

        mockMvc.perform(get("/api/v1/journeys/{journeyId}/hotel-recommendations", journeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations", hasSize(3)))
                .andExpect(jsonPath("$.recommendations[0].hotel.id").value(closest.id()))
                .andExpect(jsonPath("$.recommendations[1].hotel.id").value(midCheap.id()))
                .andExpect(jsonPath("$.recommendations[2].hotel.id").value(midPricey.id()))
                .andExpect(jsonPath("$.recommendations[0].rank").value(1))
                .andExpect(jsonPath("$.recommendations[1].rank").value(2))
                .andExpect(jsonPath("$.recommendations[2].rank").value(3));
    }

    // ------------------------------------------------------------------
    // 12. Invalid hotel data -> 400
    // ------------------------------------------------------------------
    @Test
    @DisplayName("invalid hotel payloads are rejected with 400 Bad Request")
    void invalidHotelDataIsRejected() throws Exception {
        assertBadHotelRequest("{\"name\": \"Bad\", \"pricePerNight\": -5.00}");
        assertBadHotelRequest("{\"name\": \"Bad\", \"distanceKm\": -1.00}");
        assertBadHotelRequest("{\"name\": \"Bad\", \"rating\": 9.9}");
        assertBadHotelRequest("{\"name\": \"   \"}");
        assertBadHotelRequest("{\"name\": \"Bad\", \"currency\": \"RUPEE\"}");
    }

    private void assertBadHotelRequest(String body) throws Exception {
        mockMvc.perform(post("/api/v1/hotels").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ------------------------------------------------------------------
    // 13. Unknown identifiers -> 404
    // ------------------------------------------------------------------
    @Test
    @DisplayName("unknown journey or hotel ids return the standard 404 response")
    void unknownIdentifiersReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/hotels/{hotelId}", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(get("/api/v1/journeys/{journeyId}/hotel-recommendations", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(post("/api/v1/journeys/{journeyId}/hotel-recommendations/run", 999_999_999L))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // 14. Repeat run is idempotent and stale rows expire
    // ------------------------------------------------------------------
    @Test
    @DisplayName("repeated runs do not duplicate recommendations, and a new alternative expires them")
    void repeatedRunsAreIdempotentAndExpireStaleRows() throws Exception {
        Station s1 = newStation("LA");
        Station s2 = newStation("LB");
        long trainId = newTrain("930012", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(trainId, s1.getId(), s2.getId()));
        JourneyLeg leg = legs(journeyId).get(0);
        JourneyImpact cancellation = impact(journeyId, leg, ImpactType.CANCELLATION, ImpactSeverity.CRITICAL, 0);
        createHotel("Demo Alpha (fictional)", s1, "1.00", "2500.00", "4.0");
        createHotel("Demo Bravo (fictional)", s1, "2.00", "1500.00", "3.5");

        mockMvc.perform(post("/api/v1/journeys/{journeyId}/hotel-recommendations/run", journeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionOutcome").value("OVERNIGHT_STAY_LIKELY"))
                .andExpect(jsonPath("$.recommendationsCreated").value(2))
                .andExpect(jsonPath("$.recommendationsReturned").value(2));

        // Second run: nothing new, everything unchanged.
        mockMvc.perform(post("/api/v1/journeys/{journeyId}/hotel-recommendations/run", journeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationsCreated").value(0))
                .andExpect(jsonPath("$.recommendationsUpdated").value(0))
                .andExpect(jsonPath("$.recommendationsUnchanged").value(2))
                .andExpect(jsonPath("$.recommendationsReturned").value(2));

        assertThat(hotelRecommendationRepository.findForJourney(journeyId, RecommendationStatus.AVAILABLE))
                .hasSize(2);

        // A feasible alternative now exists: the recommendation is no longer needed.
        addAvailableAlternative(journeyId, leg, cancellation, trainId);
        mockMvc.perform(post("/api/v1/journeys/{journeyId}/hotel-recommendations/run", journeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionOutcome").value("SAME_DAY_TRAVEL_FEASIBLE"))
                .andExpect(jsonPath("$.recommendationsExpired").value(2))
                .andExpect(jsonPath("$.recommendationsReturned").value(0));

        // History is preserved.
        List<HotelRecommendation> history =
                hotelRecommendationRepository.findForJourney(journeyId, RecommendationStatus.EXPIRED);
        assertThat(history).hasSize(2);
        assertThat(history).allSatisfy(row -> assertThat(row.getActiveKey()).isNull());
    }

    // ------------------------------------------------------------------
    // 15. Demo seeding is repeatable
    // ------------------------------------------------------------------
    @Test
    @DisplayName("running the demo seeder again does not create duplicate hotels")
    void demoSeedIsRepeatable() {
        long before = hotelRepository.count();
        hotelDemoDataSeeder.run(null);
        long after = hotelRepository.count();

        assertThat(after).isEqualTo(before);
        assertThat(hotelRepository.findAll())
                .filteredOn(hotel -> hotel.getAvailabilityStatus() == HotelAvailabilityStatus.DEMO_DATA)
                .allSatisfy(hotel -> assertThat(hotel.getName()).contains("fictional"));
    }

    // ------------------------------------------------------------------
    // 16. Empty search result
    // ------------------------------------------------------------------
    @Test
    @DisplayName("a filter with no matches returns an empty page, not an error")
    void emptySearchReturnsEmptyPage() throws Exception {
        Station station = newStation("MA");

        mockMvc.perform(get("/api/v1/hotels").param("stationId", station.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ------------------------------------------------------------------
    // 17. Regression across all phases
    // ------------------------------------------------------------------
    @Test
    @DisplayName("Phase 0-6 endpoints still respond after the Phase 7 additions")
    void earlierPhaseEndpointsStillRespond() throws Exception {
        mockMvc.perform(get("/api/v1/stations")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/trains")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/journeys")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/disruptions")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/impacts")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/recommendations/run")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/hotels")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private HotelDecisionOutcome performGetOutcome(long journeyId) {
        try {
            String json = mockMvc.perform(get("/api/v1/journeys/{journeyId}/hotel-recommendations", journeyId))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            String marker = "\"decisionOutcome\":\"";
            int start = json.indexOf(marker) + marker.length();
            int end = json.indexOf('"', start);
            return HotelDecisionOutcome.valueOf(json.substring(start, end));
        } catch (Exception exception) {
            throw new AssertionError("Failed to evaluate journey " + journeyId, exception);
        }
    }

    /**
     * Creates an ACTIVE journey impact (with the disruption event it needs) so a
     * single decision rule can be exercised without running the Phase 4/5
     * pipeline. {@code leg} is {@code null} for a journey-level impact.
     */
    private JourneyImpact impact(long journeyId, JourneyLeg leg, ImpactType type, ImpactSeverity severity,
                                 Integer delayMinutes) {
        Train eventTrain = leg == null
                ? trainRepository.getReferenceById(trainRepository.findAll().get(0).getId())
                : leg.getTrain();

        DisruptionEvent event = new DisruptionEvent();
        event.setTrain(eventTrain);
        event.setServiceDate(journeyDate);
        event.setDisruptionType(type == ImpactType.CANCELLATION
                ? DisruptionType.CANCELLATION : DisruptionType.SIGNIFICANT_DELAY);
        event.setObservedTrainStatus(type == ImpactType.CANCELLATION
                ? TrainRunningStatus.CANCELLED : TrainRunningStatus.DELAYED);
        event.setDelayMinutes(delayMinutes == null ? 0 : delayMinutes);
        event.setReason("Phase 7 test event");
        event.setDetectedAt(OffsetDateTime.now());
        event.setLastObservedAt(OffsetDateTime.now());
        event.setEventState(EventState.ACTIVE);
        disruptionEventRepository.save(event);

        JourneyImpact impact = new JourneyImpact();
        impact.setJourney(journeyRepository.getReferenceById(journeyId));
        impact.setJourneyLeg(leg);
        impact.setDisruptionEvent(event);
        impact.setImpactType(type);
        impact.setSeverity(severity);
        impact.setDelayMinutes(delayMinutes);
        impact.setExplanation("Phase 7 test impact");
        impact.setDetectedAt(OffsetDateTime.now());
        impact.setLastObservedAt(OffsetDateTime.now());
        impact.setImpactState(ImpactState.ACTIVE);
        return journeyImpactRepository.save(impact);
    }

    private void addAvailableAlternative(long journeyId, JourneyLeg leg, JourneyImpact impact, long trainId) {
        TrainRecommendation recommendation = new TrainRecommendation();
        recommendation.setJourney(journeyRepository.getReferenceById(journeyId));
        recommendation.setJourneyLeg(leg);
        recommendation.setJourneyImpact(impact);
        recommendation.setImpactType(impact.getImpactType());
        recommendation.setTrain(trainRepository.getReferenceById(trainId));
        recommendation.setServiceDate(journeyDate);
        recommendation.setTransferCount(0);
        recommendation.setScore(0L);
        recommendation.setExplanation("Phase 7 test alternative");
        recommendation.setRecommendationStatus(RecommendationStatus.AVAILABLE);
        recommendation.setActiveKey("TEST:" + System.nanoTime());
        trainRecommendationRepository.save(recommendation);
    }

    private HotelResponse createHotel(String name, Station station, String distance, String price, String rating) {
        return createHotel(name, station, distance, price, rating, "INR", null, true);
    }

    private HotelResponse createHotel(String name, Station station, String distance, String price, String rating,
                                      String currency, String amenities, boolean active) {
        return hotelService.create(new HotelRequest(
                name,
                station == null ? null : station.getId(),
                "Test locality",
                decimal(distance),
                decimal(price),
                currency,
                decimal(rating),
                amenities,
                active));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private long createJourney(LocalDate serviceDate, LegSpec... legs) {
        long passengerId = newPassenger();
        List<JourneyLegRequest> requests = new ArrayList<>();
        for (int i = 0; i < legs.length; i++) {
            LegSpec leg = legs[i];
            requests.add(new JourneyLegRequest(leg.trainId(), leg.sourceId(), leg.destinationId(), i + 1));
        }
        return journeyService.create(new JourneyRequest(passengerId, serviceDate, requests)).id();
    }

    private List<JourneyLeg> legs(long journeyId) {
        return journeyLegRepository.findLegsWithDetails(journeyId);
    }

    private long newPassenger() {
        Passenger passenger = new Passenger();
        passenger.setFullName("Hotel Tester " + SEQ.incrementAndGet());
        passenger.setEmail("hotel-" + SEQ.get() + "-" + System.nanoTime() + "@example.com");
        passenger.setActive(true);
        return passengerRepository.save(passenger).getId();
    }

    private Station newStation(String prefix) {
        Station station = new Station();
        station.setStationCode(prefix + SEQ.incrementAndGet());
        station.setName("Station " + prefix + SEQ.get());
        station.setCity("Demo");
        station.setActive(true);
        return stationRepository.save(station);
    }

    private long newTrain(String number, Station source, Station destination) {
        Train train = new Train();
        train.setTrainNumber(number);
        train.setName("Hotel Demo " + number);
        train.setSourceStation(source);
        train.setDestinationStation(destination);
        train.setActive(true);
        return trainRepository.save(train).getId();
    }

    private void addStop(long trainId, Station station, int sequence, String arrival, String departure,
                         int dayOffset) {
        TrainStop stop = new TrainStop();
        stop.setTrain(trainRepository.getReferenceById(trainId));
        stop.setStation(station);
        stop.setStopSequence(sequence);
        stop.setArrivalTime(arrival == null ? null : LocalTime.parse(arrival));
        stop.setDepartureTime(departure == null ? null : LocalTime.parse(departure));
        stop.setDayOffset(dayOffset);
        stop.setDistanceFromSourceKm(sequence * 100);
        trainStopRepository.save(stop);
    }

    private record LegSpec(long trainId, long sourceId, long destinationId) {
    }
}
