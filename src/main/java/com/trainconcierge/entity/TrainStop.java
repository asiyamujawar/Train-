package com.trainconcierge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;

/**
 * A station at which a train stops, ordered by {@link #stopSequence}.
 *
 * <p>Scheduled times are {@link LocalTime} values: a railway timetable is
 * expressed in local station time. Overnight journeys are represented with
 * {@link #dayOffset} (days after the train's scheduled starting day) instead of
 * inventing dates, which keeps the model simple and avoids claiming that this
 * demo data is a dated, live timetable.</p>
 */
@Entity
@Table(name = "train_stops")
public class TrainStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "train_id", nullable = false)
    private Train train;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    /** Positive, ordered position of this stop within the train's timetable. */
    @Column(name = "stop_sequence", nullable = false)
    private Integer stopSequence;

    @Column(name = "arrival_time")
    private LocalTime arrivalTime;

    @Column(name = "departure_time")
    private LocalTime departureTime;

    /** Non-negative number of days after the train's starting day. */
    @Column(name = "day_offset", nullable = false)
    private Integer dayOffset = 0;

    @Column(name = "distance_from_source_km")
    private Integer distanceFromSourceKm;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Train getTrain() {
        return train;
    }

    public void setTrain(Train train) {
        this.train = train;
    }

    public Station getStation() {
        return station;
    }

    public void setStation(Station station) {
        this.station = station;
    }

    public Integer getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(Integer stopSequence) {
        this.stopSequence = stopSequence;
    }

    public LocalTime getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(LocalTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalTime departureTime) {
        this.departureTime = departureTime;
    }

    public Integer getDayOffset() {
        return dayOffset;
    }

    public void setDayOffset(Integer dayOffset) {
        this.dayOffset = dayOffset;
    }

    public Integer getDistanceFromSourceKm() {
        return distanceFromSourceKm;
    }

    public void setDistanceFromSourceKm(Integer distanceFromSourceKm) {
        this.distanceFromSourceKm = distanceFromSourceKm;
    }
}
