/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.DefaultRoutingModules;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TransitRouterWrapper;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.pt.router.FakeFacility;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.testcases.MatsimTestCase;
import org.matsim.testcases.MatsimTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Most of these tests were copied from org.matsim.pt.router.TransitRouterImplTest
 * and only minimally adapted to make them run with SwissRailRaptor.
 *
 * @author mrieser / SBB
 */
public class SwissRailRaptorTest {

    private SwissRailRaptor createTransitRouter(TransitSchedule schedule, RaptorConfig raptorConfig) {
        SwissRailRaptorData data = SwissRailRaptorData.create(schedule, raptorConfig);
        SwissRailRaptor raptor = new SwissRailRaptor(data, new LeastCostRaptorRouteSelector());
        return raptor;
    }

    @Test
    public void testSingleLine() {
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        Coord fromCoord = new Coord(3800, 5100);
        Coord toCoord = new Coord(16100, 5050);
        List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), 5.0*3600, null);
        assertEquals(3, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(TransportMode.egress_walk, legs.get(2).getMode());
        assertTrue("expected TransitRoute in leg.", legs.get(1).getRoute() instanceof ExperimentalTransitRoute);
        ExperimentalTransitRoute ptRoute = (ExperimentalTransitRoute) legs.get(1).getRoute();
        assertEquals(Id.create("0", TransitStopFacility.class), ptRoute.getAccessStopId());
        assertEquals(Id.create("6", TransitStopFacility.class), ptRoute.getEgressStopId());
        assertEquals(f.blueLine.getId(), ptRoute.getLineId());
        assertEquals(Id.create("blue A > I", TransitRoute.class), ptRoute.getRouteId());
        double actualTravelTime = 0.0;
        for (Leg leg : legs) {
            actualTravelTime += leg.getTravelTime();
        }
        double expectedTravelTime = 29.0 * 60 + // agent takes the *:06 course, arriving in D at *:29
                CoordUtils.calcEuclideanDistance(f.schedule.getFacilities().get(Id.create("6", TransitStopFacility.class)).getCoord(), toCoord) / raptorConfig.getBeelineWalkSpeed();
        assertEquals(Math.ceil(expectedTravelTime), actualTravelTime, MatsimTestCase.EPSILON);
    }

    @Test
    public void testWalkDurations() {
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        Coord fromCoord = new Coord(3800, 5100);
        Coord toCoord = new Coord(16100, 5050);
        List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), 5.0*3600, null);
        assertEquals(3, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(TransportMode.egress_walk, legs.get(2).getMode());

        double expectedAccessWalkTime = CoordUtils.calcEuclideanDistance(f.schedule.getFacilities().get(Id.create("0", TransitStopFacility.class)).getCoord(), fromCoord) / raptorConfig.getBeelineWalkSpeed();
        assertEquals(Math.ceil(expectedAccessWalkTime), legs.get(0).getTravelTime(), MatsimTestUtils.EPSILON);
        double expectedEgressWalkTime = CoordUtils.calcEuclideanDistance(f.schedule.getFacilities().get(Id.create("6", TransitStopFacility.class)).getCoord(), toCoord) / raptorConfig.getBeelineWalkSpeed();
        assertEquals(Math.ceil(expectedEgressWalkTime), legs.get(2).getTravelTime(), MatsimTestUtils.EPSILON);
    }

    @Test
    public void testWalkDurations_range() {
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        SwissRailRaptor router = createTransitRouter(f.schedule, raptorConfig);
        Coord fromCoord = new Coord(3800, 5100);
        Coord toCoord = new Coord(16100, 5050);
        double depTime = 5.0 * 3600;
        List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), depTime - 300, depTime, depTime + 300, null);
        assertEquals(3, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(TransportMode.egress_walk, legs.get(2).getMode());

        double expectedAccessWalkTime = CoordUtils.calcEuclideanDistance(f.schedule.getFacilities().get(Id.create("0", TransitStopFacility.class)).getCoord(), fromCoord) / raptorConfig.getBeelineWalkSpeed();
        assertEquals(Math.ceil(expectedAccessWalkTime), legs.get(0).getTravelTime(), MatsimTestUtils.EPSILON);
        double expectedEgressWalkTime = CoordUtils.calcEuclideanDistance(f.schedule.getFacilities().get(Id.create("6", TransitStopFacility.class)).getCoord(), toCoord) / raptorConfig.getBeelineWalkSpeed();
        assertEquals(Math.ceil(expectedEgressWalkTime), legs.get(2).getTravelTime(), MatsimTestUtils.EPSILON);
    }

    @Test
    public void testFromToSameStop() {
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        Coord fromCoord = new Coord(3800, 5100);
        Coord toCoord = new Coord(4100, 5050);
        List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), 5.0*3600, null);
        assertEquals(1, legs.size());
        assertEquals(TransportMode.transit_walk, legs.get(0).getMode());
        double actualTravelTime = 0.0;
        for (Leg leg : legs) {
            actualTravelTime += leg.getTravelTime();
        }
        double expectedTravelTime = CoordUtils.calcEuclideanDistance(fromCoord, toCoord) / raptorConfig.getBeelineWalkSpeed();
        assertEquals(expectedTravelTime, actualTravelTime, MatsimTestCase.EPSILON);
    }

    @Test
    public void testDirectWalkCheaper() {
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        Coord fromCoord = new Coord(4000, 3000);
        Coord toCoord = new Coord(8000, 3000);
        List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), 5.0*3600, null);
        assertEquals(1, legs.size());
        assertEquals(TransportMode.transit_walk, legs.get(0).getMode());
        double actualTravelTime = 0.0;
        for (Leg leg : legs) {
            actualTravelTime += leg.getTravelTime();
        }
        double expectedTravelTime = CoordUtils.calcEuclideanDistance(fromCoord, toCoord) / raptorConfig.getBeelineWalkSpeed();
        assertEquals(expectedTravelTime, actualTravelTime, MatsimTestCase.EPSILON);
    }


    @Test
    public void testSingleLine_DifferentWaitingTime() {
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        Coord fromCoord = new Coord(4000, 5002);
        Coord toCoord = new Coord(8000, 5002);

        double inVehicleTime = 7.0*60; // travel time from A to B
        for (int min = 0; min < 30; min += 3) {
            List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), 5.0*3600 + min*60, null);
            assertEquals(3, legs.size()); // walk-pt-walk
            double actualTravelTime = 0.0;
            for (Leg leg : legs) {
                actualTravelTime += leg.getTravelTime();
            }
            double waitingTime = ((46 - min) % 20) * 60; // departures at *:06 and *:26 and *:46
            assertEquals("expected different waiting time at 05:"+min, waitingTime, actualTravelTime - inVehicleTime, MatsimTestCase.EPSILON);
        }
    }

    @Test
    public void testLineChange() {
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        Coord toCoord = new Coord(16100, 10050);
        List<Leg> legs = router.calcRoute(new FakeFacility(new Coord(3800, 5100)), new FakeFacility(toCoord), 6.0*3600, null);
        assertEquals(5, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(TransportMode.transit_walk, legs.get(2).getMode());
        assertEquals(TransportMode.pt, legs.get(3).getMode());
        assertEquals(TransportMode.egress_walk, legs.get(4).getMode());
        assertTrue("expected TransitRoute in leg.", legs.get(1).getRoute() instanceof ExperimentalTransitRoute);
        ExperimentalTransitRoute ptRoute = (ExperimentalTransitRoute) legs.get(1).getRoute();
        assertEquals(Id.create("0", TransitStopFacility.class), ptRoute.getAccessStopId());
        assertEquals(Id.create("4", TransitStopFacility.class), ptRoute.getEgressStopId());
        assertEquals(f.blueLine.getId(), ptRoute.getLineId());
        assertEquals(Id.create("blue A > I", TransitRoute.class), ptRoute.getRouteId());
        assertTrue("expected TransitRoute in leg.", legs.get(3).getRoute() instanceof ExperimentalTransitRoute);
        ptRoute = (ExperimentalTransitRoute) legs.get(3).getRoute();
        assertEquals(Id.create("18", TransitStopFacility.class), ptRoute.getAccessStopId());
        assertEquals(Id.create("19", TransitStopFacility.class), ptRoute.getEgressStopId());
        assertEquals(f.greenLine.getId(), ptRoute.getLineId());
        assertEquals(Id.create("green clockwise", TransitRoute.class), ptRoute.getRouteId());
        double actualTravelTime = 0.0;
        for (Leg leg : legs) {
            actualTravelTime += leg.getTravelTime();
        }
        double expectedTravelTime = 31.0 * 60 + // agent takes the *:06 course, arriving in C at *:18, departing at *:21, arriving in K at*:31
                CoordUtils.calcEuclideanDistance(f.schedule.getFacilities().get(Id.create("19", TransitStopFacility.class)).getCoord(), toCoord) / raptorConfig.getBeelineWalkSpeed();
        assertEquals(Math.ceil(expectedTravelTime), actualTravelTime, MatsimTestCase.EPSILON);
    }

    @Test
    public void testFasterAlternative() {
        /* idea: travel from A to G
         * One could just take the blue line and travel from A to G (dep *:46, arrival *:28),
         * or one could first travel from A to C (dep *:46, arr *:58), and then take the red line
         * from C to G (dep *:00, arr *:09), but this requires an additional transfer (but
         * at the same StopFacility, so there should not be a transit_walk-leg).
         */
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        Coord toCoord = new Coord(28100, 4950);
        List<Leg> legs = router.calcRoute(new FakeFacility( new Coord(3800, 5100)), new FakeFacility(toCoord), 5.0*3600 + 40.0*60, null);
        assertEquals("wrong number of legs", 4, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(TransportMode.pt, legs.get(2).getMode());
        assertEquals(TransportMode.egress_walk, legs.get(3).getMode());
        assertTrue("expected TransitRoute in leg.", legs.get(1).getRoute() instanceof ExperimentalTransitRoute);
        ExperimentalTransitRoute ptRoute = (ExperimentalTransitRoute) legs.get(1).getRoute();
        assertEquals(Id.create("0", TransitStopFacility.class), ptRoute.getAccessStopId());
        assertEquals(Id.create("4", TransitStopFacility.class), ptRoute.getEgressStopId());
        assertEquals(f.blueLine.getId(), ptRoute.getLineId());
        assertEquals(Id.create("blue A > I", TransitRoute.class), ptRoute.getRouteId());
        assertTrue("expected TransitRoute in leg.", legs.get(2).getRoute() instanceof ExperimentalTransitRoute);
        ptRoute = (ExperimentalTransitRoute) legs.get(2).getRoute();
        assertEquals(Id.create("4", TransitStopFacility.class), ptRoute.getAccessStopId());
        assertEquals(Id.create("12", TransitStopFacility.class), ptRoute.getEgressStopId());
        assertEquals(f.redLine.getId(), ptRoute.getLineId());
        assertEquals(Id.create("red C > G", TransitRoute.class), ptRoute.getRouteId());
        double actualTravelTime = 0.0;
        for (Leg leg : legs) {
            actualTravelTime += leg.getTravelTime();
        }
        double expectedTravelTime = 29.0 * 60 + // agent takes the *:46 course, arriving in C at *:58, departing at *:00, arriving in G at*:09
                CoordUtils.calcEuclideanDistance(f.schedule.getFacilities().get(Id.create("12", TransitStopFacility.class)).getCoord(), toCoord) / raptorConfig.getBeelineWalkSpeed();
        assertEquals(Math.ceil(expectedTravelTime), actualTravelTime, MatsimTestCase.EPSILON);
    }


    @Test
    public void testTransferWeights() {
        /* idea: travel from C to F
         * If starting at the right time, one could take the red line to G and travel back with blue to F.
         * If one doesn't want to switch lines, one could take the blue line from C to F directly.
         * Using the red line (dep *:00, change at G *:09/*:12) results in an arrival time of *:19,
         * using the blue line only (dep *:02) results in an arrival time of *:23. So the line switch
         * cost must be larger than 4 minutes to have an effect.
         */
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        raptorConfig.setTransferPenaltyCost(0);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        List<Leg> legs = router.calcRoute(new FakeFacility(new Coord(11900, 5100)), new FakeFacility(new Coord(24100, 4950)), 6.0*3600 - 5.0*60, null);
        assertEquals("wrong number of legs", 5, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(f.redLine.getId(), ((ExperimentalTransitRoute) legs.get(1).getRoute()).getLineId());
        assertEquals(TransportMode.transit_walk, legs.get(2).getMode());
        assertEquals(TransportMode.pt, legs.get(3).getMode());
        assertEquals(f.blueLine.getId(), ((ExperimentalTransitRoute) legs.get(3).getRoute()).getLineId());
        assertEquals(TransportMode.egress_walk, legs.get(4).getMode());

        raptorConfig.setTransferPenaltyCost(-300.0 * raptorConfig.getMarginalUtilityOfTravelTimePt_utl_s()); // corresponds to 5 minutes transit travel time
        router = createTransitRouter(f.schedule, raptorConfig);
        legs = router.calcRoute(new FakeFacility(new Coord(11900, 5100)), new FakeFacility(new Coord(24100, 4950)), 6.0*3600 - 5.0*60, null);
        assertEquals(3, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(f.blueLine.getId(), ((ExperimentalTransitRoute) legs.get(1).getRoute()).getLineId());
        assertEquals(TransportMode.egress_walk, legs.get(2).getMode());
    }

    @Test
    public void testTransferTime() {
        /* idea: travel from C to F
         * If starting at the right time, one could take the red line to G and travel back with blue to F.
         * If one doesn't want to switch lines, one could take the blue line from C to F directly.
         * Using the red line (dep *:00, change at G *:09/*:12) results in an arrival time of *:19,
         * using the blue line only (dep *:02) results in an arrival time of *:23.
         * For the line switch at G, 3 minutes are available. If the minimum transfer time is larger than
         * that, the direct connection should be taken.
         */
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        raptorConfig.setTransferPenaltyCost(0);
        assertEquals(0, raptorConfig.getMinimalTransferTime(), 1e-8);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        List<Leg> legs = router.calcRoute(new FakeFacility(new Coord(11900, 5100)), new FakeFacility(new Coord(24100, 4950)), 6.0*3600 - 5.0*60, null);
        assertEquals("wrong number of legs",5, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(f.redLine.getId(), ((ExperimentalTransitRoute) legs.get(1).getRoute()).getLineId());
        assertEquals(TransportMode.transit_walk, legs.get(2).getMode());
        assertEquals(TransportMode.pt, legs.get(3).getMode());
        assertEquals(f.blueLine.getId(), ((ExperimentalTransitRoute) legs.get(3).getRoute()).getLineId());
        assertEquals(TransportMode.egress_walk, legs.get(4).getMode());

        raptorConfig.setMinimalTransferTime(3*60+1); // just a little bit more than 3 minutes, so we miss the connection at G
        router = createTransitRouter(f.schedule, raptorConfig); // this is necessary to update the router for any change in config.
        legs = router.calcRoute(new FakeFacility(new Coord(11900, 5100)), new FakeFacility(new Coord(24100, 4950)), 6.0*3600 - 5.0*60, null);
        assertEquals("wrong number of legs",3, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(f.blueLine.getId(), ((ExperimentalTransitRoute) legs.get(1).getRoute()).getLineId());
        assertEquals(TransportMode.egress_walk, legs.get(2).getMode());
    }


    @Test
    public void testAfterMidnight() {
        // in contrast to the default PT router, SwissRailRaptor will not automatically
        // repeat the schedule after 24 hours, so any agent departing late will have to walk if there
        // is no late service in the schedule.
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        Coord fromCoord = new Coord(3800, 5100);
        Coord toCoord = new Coord(16100, 5050);
        List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), 25.0*3600, null);
        assertEquals(1, legs.size());
        assertEquals(TransportMode.transit_walk, legs.get(0).getMode());
        assertTrue("expected GenericRoute in leg.", legs.get(0).getRoute() instanceof GenericRouteImpl);
        double actualTravelTime = 0.0;
        for (Leg leg : legs) {
            actualTravelTime += leg.getTravelTime();
        }
        double expectedTravelTime = CoordUtils.calcEuclideanDistance(fromCoord, toCoord) / raptorConfig.getBeelineWalkSpeed();
        assertEquals(expectedTravelTime, actualTravelTime, MatsimTestCase.EPSILON);
    }

    @Test
    public void testCoordFarAway() {
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        double x = +42000;
        double x1 = -2000;
        List<Leg> legs = router.calcRoute(new FakeFacility(new Coord(x1, 0)), new FakeFacility(new Coord(x, 0)), 5.5*3600, null); // should map to stops A and I
        assertEquals(3, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(TransportMode.egress_walk, legs.get(2).getMode());
        assertTrue("expected TransitRoute in leg.", legs.get(1).getRoute() instanceof ExperimentalTransitRoute);
        ExperimentalTransitRoute ptRoute = (ExperimentalTransitRoute) legs.get(1).getRoute();
        assertEquals(Id.create("0", TransitStopFacility.class), ptRoute.getAccessStopId());
        assertEquals(Id.create("16", TransitStopFacility.class), ptRoute.getEgressStopId());
        assertEquals(f.blueLine.getId(), ptRoute.getLineId());
        assertEquals(Id.create("blue A > I", TransitRoute.class), ptRoute.getRouteId());
    }

    /**
     * Tests that if only a single transfer-/walk-link is found, the router correctly only returns
     * on walk leg from start to end.
     */
    @Test
    public void testSingleWalkOnly() {
        WalkFixture f = new WalkFixture();
        f.routerConfig.setSearchRadius(0.8 * CoordUtils.calcEuclideanDistance(f.coord2, f.coord4));
        f.routerConfig.setExtensionRadius(0.0);

        TransitRouter router = createTransitRouter(f.schedule, f.routerConfig);
        List<Leg> legs = router.calcRoute(new FakeFacility(f.coord2), new FakeFacility(f.coord4), 990, null);
        assertEquals(1, legs.size());
        assertEquals(TransportMode.transit_walk, legs.get(0).getMode());
    }


    /**
     * Tests that if only exactly two transfer-/walk-link are found, the router correctly only returns
     * on walk leg from start to end. Differs from {@link #testSingleWalkOnly()} in that it tests for
     * the correct internal working when more than one walk links are returned.
     */
    @Test
    public void testDoubleWalkOnly() {
        WalkFixture f = new WalkFixture();
        f.routerConfig.setSearchRadius(0.8 * CoordUtils.calcEuclideanDistance(f.coord2, f.coord4));
        f.routerConfig.setExtensionRadius(0.0);

        TransitRouter router = createTransitRouter(f.schedule, f.routerConfig);
        List<Leg> legs = router.calcRoute(new FakeFacility(f.coord2), new FakeFacility(f.coord6), 990, null);
        assertEquals(1, legs.size());
        assertEquals(TransportMode.transit_walk, legs.get(0).getMode());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testLongTransferTime_withTransitRouterWrapper() {
        // 5 minutes additional transfer time
        {
            TransferFixture f = new TransferFixture(5 * 60.0);
            TransitRouter router = createTransitRouter(f.schedule, f.routerConfig);
            Coord fromCoord = f.fromFacility.getCoord();
            Coord toCoord = f.toFacility.getCoord();
            List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), 7.0*3600 + 50*60, null);
            double legDuration = calcTripDuration(new ArrayList<>(legs));
            Assert.assertEquals(5, legs.size());
            Assert.assertEquals(100, legs.get(0).getTravelTime(), 0.0);	// 0.1km with 1m/s walk speed -> 100s; arrival at 07:51:40
            Assert.assertEquals(800, legs.get(1).getTravelTime(), 0.0);	// 8m 20s waiting for pt departure and 5m pt travel time -> 500s + 300s = 800s; arrival at 08:05:00
            Assert.assertEquals(300, legs.get(2).getTravelTime(), 0.0);	// 0.004km with 1m/s walk speed, but minimal waiting time -> max(4s, 300s) = 300s; arrival at 08:10:00
            Assert.assertEquals(600, legs.get(3).getTravelTime(), 0.0);	// 5m 00s waiting for pt departure and 5m pt travel time -> 300s + 300s = 600s; arrival at 08:15:00
            Assert.assertEquals(100, legs.get(4).getTravelTime(), 0.0);	// 0.1km with 1m/s walk speed -> 100s
            Assert.assertEquals(1900.0, legDuration, 0.0);

            RoutingModule walkRoutingModule = DefaultRoutingModules.createTeleportationRouter(TransportMode.transit_walk, f.scenario.getPopulation().getFactory(),
                    f.config.plansCalcRoute().getModeRoutingParams().get(TransportMode.walk));

            TransitRouterWrapper wrapper = new TransitRouterWrapper(
                    router,
                    f.schedule,
                    f.scenario.getNetwork(), // use a walk router in case no PT path is found
                    walkRoutingModule);

            List<PlanElement> planElements = (List<PlanElement>) wrapper.calcRoute(f.fromFacility, f.toFacility, 7.0*3600 + 50*60, null);
            double tripDuration = calcTripDuration(planElements);
            Assert.assertEquals(9, planElements.size());
            Assert.assertEquals(100, ((Leg) planElements.get(0)).getTravelTime(), 0.0);	// 0.1km with 1m/s walk speed -> 100s; arrival at 07:51:40
            Assert.assertEquals(800, ((Leg) planElements.get(2)).getTravelTime(), 0.0);	// 8m 20s waiting for pt departure and 5m pt travel time -> 500s + 300s = 800s; arrival at 08:05:00
            Assert.assertEquals(300, ((Leg) planElements.get(4)).getTravelTime(), 0.0);	// 0.004km with 1m/s walk speed, but minimal waiting time -> max(4s, 300s) = 300s; arrival at 08:10:00
            Assert.assertEquals(600, ((Leg) planElements.get(6)).getTravelTime(), 0.0);	// 5m 00s waiting for pt departure and 5m pt travel time -> 300s + 300s = 600s; arrival at 08:15:00
            Assert.assertEquals(100, ((Leg) planElements.get(8)).getTravelTime(), 0.0);	// 0.1km with 1m/s walk speed -> 100s
            Assert.assertEquals(1900.0, tripDuration, 0.0);
        }

        // 65 minutes additional transfer time - miss one departure
        {
            TransferFixture f = new TransferFixture(65 * 60.0);
            TransitRouter router = createTransitRouter(f.schedule, f.routerConfig);
            Coord fromCoord = f.fromFacility.getCoord();
            Coord toCoord = f.toFacility.getCoord();
            List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), 7.0*3600 + 50*60, null);
            double legDuration = calcTripDuration(new ArrayList<>(legs));
            Assert.assertEquals(5, legs.size());
            Assert.assertEquals(100, legs.get(0).getTravelTime(), 0.0);	// 0.1km with 1m/s walk speed -> 100s; arrival at 07:51:40
            Assert.assertEquals(800, legs.get(1).getTravelTime(), 0.0);	// 8m 20s waiting for pt departure and 5m pt travel time -> 500s + 300s = 800s; arrival at 08:05:00
            Assert.assertEquals(3900, legs.get(2).getTravelTime(), 0.0);	// 0.004km with 1m/s walk speed, but minimal waiting time -> max(4s, 300s) = 300s; arrival at 08:10:00
            Assert.assertEquals(600, legs.get(3).getTravelTime(), 0.0);	// 5m 00s waiting for pt departure and 5m pt travel time -> 300s + 300s = 600s; arrival at 08:15:00
            Assert.assertEquals(100, legs.get(4).getTravelTime(), 0.0);	// 0.1km with 1m/s walk speed -> 100s
            Assert.assertEquals(5500.0, legDuration, 0.0);

            RoutingModule walkRoutingModule = DefaultRoutingModules.createTeleportationRouter(TransportMode.transit_walk, f.scenario.getPopulation().getFactory(),
                    f.config.plansCalcRoute().getModeRoutingParams().get(TransportMode.walk));

            TransitRouterWrapper wrapper = new TransitRouterWrapper(
                    router,
                    f.schedule,
                    f.scenario.getNetwork(), // use a walk router in case no PT path is found
                    walkRoutingModule);

            List<PlanElement> planElements = (List<PlanElement>) wrapper.calcRoute(f.fromFacility, f.toFacility, 7.0*3600 + 50*60, null);
            double tripDuration = calcTripDuration(planElements);
            Assert.assertEquals(9, planElements.size());
            Assert.assertEquals(100, ((Leg) planElements.get(0)).getTravelTime(), 0.0);	// 0.1km with 1m/s walk speed -> 100s; arrival at 07:51:40
            Assert.assertEquals(800, ((Leg) planElements.get(2)).getTravelTime(), 0.0);	// 8m 20s waiting for pt departure and 5m pt travel time -> 500s + 300s = 800s; arrival at 08:05:00
            Assert.assertEquals(3900, ((Leg) planElements.get(4)).getTravelTime(), 0.0);	// 0.004km with 1m/s walk speed, but minimal waiting time -> max(4s, 300s) = 300s; arrival at 08:10:00
            Assert.assertEquals(600, ((Leg) planElements.get(6)).getTravelTime(), 0.0);	// 5m 00s waiting for pt departure and 5m pt travel time -> 300s + 300s = 600s; arrival at 08:15:00
            Assert.assertEquals(100, ((Leg) planElements.get(8)).getTravelTime(), 0.0);	// 0.1km with 1m/s walk speed -> 100s
            Assert.assertEquals(5500.0, tripDuration, 0.0);
        }

        // 600 minutes additional transfer time - miss all departures
        {
            TransferFixture f = new TransferFixture(600 * 60.0);
            TransitRouter router = createTransitRouter(f.schedule, f.routerConfig);
            Coord fromCoord = f.fromFacility.getCoord();
            Coord toCoord = f.toFacility.getCoord();
            List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), 7.0*3600 + 50*60, null);
            double legDuration = calcTripDuration(new ArrayList<>(legs));
            Assert.assertEquals(1, legs.size());
            Assert.assertEquals(50000, legDuration, 1.0);

            RoutingModule walkRoutingModule = DefaultRoutingModules.createTeleportationRouter(TransportMode.transit_walk, f.scenario.getPopulation().getFactory(),
                    f.config.plansCalcRoute().getModeRoutingParams().get(TransportMode.walk));

            TransitRouterWrapper routingModule = new TransitRouterWrapper(
                    router,
                    f.schedule,
                    f.scenario.getNetwork(), // use a walk router in case no PT path is found
                    walkRoutingModule);

            TransitRouterWrapper wrapper = new TransitRouterWrapper(router, f.schedule, f.scenario.getNetwork(), routingModule);
            List<PlanElement> planElements = (List<PlanElement>) wrapper.calcRoute(f.fromFacility, f.toFacility, 7.0*3600 + 50*60, null);
            double tripDuration = calcTripDuration(planElements);
            Assert.assertEquals(1, planElements.size());
            Assert.assertEquals(50000, tripDuration, 1.0);
        }
    }

    private static double calcTripDuration(List<PlanElement> planElements) {
        double duration = 0.0;
        for (PlanElement pe : planElements) {
            if (pe instanceof Activity) {
                Activity act = (Activity) pe;
                double endTime = act.getEndTime();
                double startTime = act.getStartTime();
                if (startTime != Time.UNDEFINED_TIME && endTime != Time.UNDEFINED_TIME) {
                    duration += (endTime - startTime);
                }
            } else if (pe instanceof Leg) {
                Leg leg = (Leg) pe;
                duration += leg.getTravelTime();
            }
        }
        return duration;
    }

    @Test
    public void testNightBus() {
        // test a special case where a direct connection only runs at a late time, when typically
        // no other services run anymore.
        NightBusFixture f = new NightBusFixture();

        TransitRouter router = createTransitRouter(f.schedule, f.routerConfig);
        Coord fromCoord = new Coord(5010, 1010);
        Coord toCoord = new Coord(5010, 5010);
        List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), 8.0*3600-2*60, null);
        assertEquals(5, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(TransportMode.pt, legs.get(2).getMode());
        assertEquals(TransportMode.pt, legs.get(3).getMode());
        assertEquals(TransportMode.egress_walk, legs.get(4).getMode());
        assertTrue("expected TransitRoute in leg.", legs.get(1).getRoute() instanceof ExperimentalTransitRoute);
        ExperimentalTransitRoute ptRoute = (ExperimentalTransitRoute) legs.get(1).getRoute();
        assertEquals(f.stop0.getId(), ptRoute.getAccessStopId());
        assertEquals(f.stop1.getId(), ptRoute.getEgressStopId());
        assertEquals(f.lineId0, ptRoute.getLineId());
        assertTrue("expected TransitRoute in leg.", legs.get(2).getRoute() instanceof ExperimentalTransitRoute);
        ptRoute = (ExperimentalTransitRoute) legs.get(2).getRoute();
        assertEquals(f.stop1.getId(), ptRoute.getAccessStopId());
        assertEquals(f.stop2.getId(), ptRoute.getEgressStopId());
        assertEquals(f.lineId1, ptRoute.getLineId());
        assertTrue("expected TransitRoute in leg.", legs.get(3).getRoute() instanceof ExperimentalTransitRoute);
        ptRoute = (ExperimentalTransitRoute) legs.get(3).getRoute();
        assertEquals(f.stop2.getId(), ptRoute.getAccessStopId());
        assertEquals(f.stop3.getId(), ptRoute.getEgressStopId());
        assertEquals(f.lineId3, ptRoute.getLineId());
    }

    @Test
    public void testRangeQuery() {
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        SwissRailRaptor raptor = createTransitRouter(f.schedule, raptorConfig);

        Coord fromCoord = new Coord(3800, 5100);
        Coord toCoord = new Coord(28100, 4950);
        double depTime = 5.0 * 3600 + 50 * 60;
        List<RaptorRoute> routes = raptor.calcRoutes(new FakeFacility(fromCoord), new FakeFacility(toCoord), depTime - 600, depTime, depTime + 3600, null);

        for (int i = 0; i < routes.size(); i++) {
            RaptorRoute route = routes.get(i);
            System.out.println(i + "  depTime = " + Time.writeTime(route.getDepartureTime()) + "  arrTime = " + Time.writeTime(route.getDepartureTime() + route.getTravelTime()) + "  # transfers = " + route.getNumberOfTransfers() + "  costs = " + route.totalCosts);
        }

        Assert.assertEquals(6, routes.size());

        assertRaptorRoute(routes.get(0), "05:40:12", "06:30:56", 0, 10.1466666);
        assertRaptorRoute(routes.get(1), "06:00:12", "06:50:56", 0, 10.1466666);
        assertRaptorRoute(routes.get(2), "06:20:12", "07:10:56", 0, 10.1466666);
        assertRaptorRoute(routes.get(3), "06:40:12", "07:30:56", 0, 10.1466666);
        assertRaptorRoute(routes.get(4), "05:40:12", "06:11:56", 1, 7.3466666);
        assertRaptorRoute(routes.get(5), "06:40:12", "07:11:56", 1, 7.3466666);
    }

    private void assertRaptorRoute(RaptorRoute route, String depTime, String arrTime, int expectedTransfers, double expectedCost) {
        Assert.assertEquals("wrong number of transfers", expectedTransfers, route.getNumberOfTransfers());
        Assert.assertEquals("wrong departure time", Time.parseTime(depTime), route.getDepartureTime(), 0.99);
        Assert.assertEquals("wrong arrival time", Time.parseTime(arrTime), route.getDepartureTime() + route.getTravelTime(), 0.99);
        Assert.assertEquals("wrong cost", expectedCost, route.totalCosts, 1e-5);
    }

    /** test for https://github.com/SchweizerischeBundesbahnen/matsim-sbb-extensions/issues/1
     *
     * If there are StopFacilities in the transit schedule, that are not part of any route, the Router crashes with a NPE in SwissRailRaptorData at line 213, because toRouteStopIndices == null.
     */
    @Test
    public void testUnusedTransitStop() {
        Fixture f = new Fixture();
        f.init();

        // add some unused transit stops:
        // - one close to the start coordinate, so it gets selected as start stop
        TransitStopFacility fooStop = f.schedule.getFactory().createTransitStopFacility(Id.create("foo", TransitStopFacility.class), new Coord(3900, 4900), true);
        f.schedule.addStopFacility(fooStop);
        // - one close to another stop as a potential transfer
        TransitStopFacility barStop = f.schedule.getFactory().createTransitStopFacility(Id.create("bar", TransitStopFacility.class), new Coord(12010, 4990), true);
        f.schedule.addStopFacility(barStop);
        // - one close to the end coordinate as a potential arrival stop
        TransitStopFacility bazStop = f.schedule.getFactory().createTransitStopFacility(Id.create("baz", TransitStopFacility.class), new Coord(28010, 4990), true);
        f.schedule.addStopFacility(bazStop);


        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        SwissRailRaptor raptor = createTransitRouter(f.schedule, raptorConfig);

        Coord fromCoord = new Coord(3800, 5100);
        Coord toCoord = new Coord(28100, 4950);
        double depTime = 5.0 * 3600 + 50 * 60;
        List<Leg> legs = raptor.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), depTime, null);
        // this test mostly checks that there are no Exceptions.
        Assert.assertEquals(3, legs.size());
    }

    /**
     * Generates the following network for testing:
     * <pre>
     *                (5)
     *                 |
     *                 3
     *                 |
     * (1)---1---(2)  (4)  (6)---4---(7)
     *                 |
     *                 2
     *                 |
     *                (3)
     * </pre>
     * Each link represents a transit line. Between the stops (2) and (4) and also
     * between (4) and (6) agents must walk.
     *
     * @author mrieser
     */
    private static class WalkFixture {

        /*package*/ final MutableScenario scenario;
        /*package*/ final TransitSchedule schedule;
        /*package*/ final RaptorConfig routerConfig;

        final Coord coord1;
        final Coord coord2;
        final Coord coord3;
        final Coord coord4;
        final Coord coord5;
        final Coord coord6;
        final Coord coord7;

        final TransitStopFacility stop1;
        final TransitStopFacility stop2;
        final TransitStopFacility stop3;
        final TransitStopFacility stop4;
        final TransitStopFacility stop5;
        final TransitStopFacility stop6;
        final TransitStopFacility stop7;

        /*package*/ WalkFixture() {
            this.scenario = (MutableScenario) ScenarioUtils.createScenario(ConfigUtils.createConfig());
            this.scenario.getConfig().transit().setUseTransit(true);
            this.routerConfig = RaptorUtils.createRaptorConfig(this.scenario.getConfig());
            this.routerConfig.setSearchRadius(500.0);
            this.routerConfig.setBeelineWalkConnectionDistance(100.0);
            this.routerConfig.setBeelineWalkSpeed(10.0); // so the agents can walk the distance in 10 seconds

            double x = 0;
            this.coord1 = new Coord(x, (double) 0);
            x += 1000;
            this.coord2 = new Coord(x, (double) 0);
            x += (this.routerConfig.getBeelineWalkConnectionDistance() * 0.75);
            double y = -1000;
            this.coord3 = new Coord(x, y);
            this.coord4 = new Coord(x, (double) 0);
            this.coord5 = new Coord(x, (double) 1000);
            x += (this.routerConfig.getBeelineWalkConnectionDistance() * 0.75);
            this.coord6 = new Coord(x, (double) 0);
            x += 1000;
            this.coord7 = new Coord(x, (double) 0);

            // network
            Network network = this.scenario.getNetwork();
            Node node1 = network.getFactory().createNode(Id.create("1", Node.class), this.coord1);
            Node node2 = network.getFactory().createNode(Id.create("2", Node.class), this.coord2);
            Node node3 = network.getFactory().createNode(Id.create("3", Node.class), this.coord3);
            Node node4 = network.getFactory().createNode(Id.create("4", Node.class), this.coord4);
            Node node5 = network.getFactory().createNode(Id.create("5", Node.class), this.coord5);
            Node node6 = network.getFactory().createNode(Id.create("6", Node.class), this.coord6);
            Node node7 = network.getFactory().createNode(Id.create("7", Node.class), this.coord7);
            network.addNode(node1);
            network.addNode(node2);
            network.addNode(node3);
            network.addNode(node4);
            network.addNode(node5);
            network.addNode(node6);
            network.addNode(node7);
            Link link1 = network.getFactory().createLink(Id.create("1", Link.class), node1, node2);
            Link link2 = network.getFactory().createLink(Id.create("2", Link.class), node3, node4);
            Link link3 = network.getFactory().createLink(Id.create("3", Link.class), node4, node5);
            Link link4 = network.getFactory().createLink(Id.create("4", Link.class), node6, node7);
            network.addLink(link1);
            network.addLink(link2);
            network.addLink(link3);
            network.addLink(link4);

            // schedule
            this.schedule = this.scenario.getTransitSchedule();
            TransitScheduleFactory sb = this.schedule.getFactory();

            this.stop1 = sb.createTransitStopFacility(Id.create("1", TransitStopFacility.class), this.coord1, false);
            this.stop2 = sb.createTransitStopFacility(Id.create("2", TransitStopFacility.class), this.coord2, false);
            this.stop3 = sb.createTransitStopFacility(Id.create("3", TransitStopFacility.class), this.coord3, false);
            this.stop4 = sb.createTransitStopFacility(Id.create("4", TransitStopFacility.class), this.coord4, false);
            this.stop5 = sb.createTransitStopFacility(Id.create("5", TransitStopFacility.class), this.coord5, false);
            this.stop6 = sb.createTransitStopFacility(Id.create("6", TransitStopFacility.class), this.coord6, false);
            this.stop7 = sb.createTransitStopFacility(Id.create("7", TransitStopFacility.class), this.coord7, false);
            this.stop1.setLinkId(link1.getId());
            this.stop2.setLinkId(link1.getId());
            this.stop3.setLinkId(link2.getId());
            this.stop4.setLinkId(link2.getId());
            this.stop5.setLinkId(link3.getId());
            this.stop6.setLinkId(link4.getId());
            this.stop7.setLinkId(link4.getId());
            this.schedule.addStopFacility(this.stop1);
            this.schedule.addStopFacility(this.stop2);
            this.schedule.addStopFacility(this.stop3);
            this.schedule.addStopFacility(this.stop4);
            this.schedule.addStopFacility(this.stop5);
            this.schedule.addStopFacility(this.stop6);
            this.schedule.addStopFacility(this.stop7);

            { // line 1
                TransitLine tLine = sb.createTransitLine(Id.create("1", TransitLine.class));
                {
                    NetworkRoute netRoute = new LinkNetworkRouteImpl(link1.getId(), link1.getId());
                    List<TransitRouteStop> stops = new ArrayList<>(2);
                    stops.add(sb.createTransitRouteStop(this.stop1, 0, 0));
                    stops.add(sb.createTransitRouteStop(this.stop2, 50, 50));
                    TransitRoute tRoute = sb.createTransitRoute(Id.create("1a", TransitRoute.class), netRoute, stops, "bus");
                    tRoute.addDeparture(sb.createDeparture(Id.create("1a1", Departure.class), 1000));
                    tLine.addRoute(tRoute);
                }
                this.schedule.addTransitLine(tLine);
            }

            { // line 2
                TransitLine tLine = sb.createTransitLine(Id.create("2", TransitLine.class));
                {
                    NetworkRoute netRoute = new LinkNetworkRouteImpl(link2.getId(), link3.getId());
                    List<TransitRouteStop> stops = new ArrayList<>(3);
                    stops.add(sb.createTransitRouteStop(this.stop3, 0, 0));
                    stops.add(sb.createTransitRouteStop(this.stop4, 50, 50));
                    stops.add(sb.createTransitRouteStop(this.stop5, 100, 100));
                    TransitRoute tRoute = sb.createTransitRoute(Id.create("2a", TransitRoute.class), netRoute, stops, "bus");
                    tRoute.addDeparture(sb.createDeparture(Id.create("2a1", Departure.class), 1000));
                    tLine.addRoute(tRoute);
                }
                this.schedule.addTransitLine(tLine);
            }

            { // line 3
                TransitLine tLine = sb.createTransitLine(Id.create("3", TransitLine.class));
                {
                    NetworkRoute netRoute = new LinkNetworkRouteImpl(link4.getId(), link4.getId());
                    List<TransitRouteStop> stops = new ArrayList<>(2);
                    stops.add(sb.createTransitRouteStop(this.stop6, 0, 0));
                    stops.add(sb.createTransitRouteStop(this.stop7, 50, 50));
                    TransitRoute tRoute = sb.createTransitRoute(Id.create("3a", TransitRoute.class), netRoute, stops, "train");
                    tRoute.addDeparture(sb.createDeparture(Id.create("3a1", Departure.class), 1070));
                    tLine.addRoute(tRoute);
                }
                this.schedule.addTransitLine(tLine);
            }
        }

    }

    /**
     * Generates the following network for testing:
     * <pre>
     *  (n) node
     *  [s] stop facilities
     *   l  link
     *
     *  [0]       [1]
     *  (0)---0---(1)---1---(2)
     *            [2]       [3]
     *
     * </pre>
     *
     * Simple setup with one line from 0 to 1 and one from 2 to 3.
     *
     * Departures are every 5 minutes. PT travel time from (1) to (2) and from (2) to (1) is one hour.
     * A short cut is realized via an entry in the transfer matrix (5 minutes).
     *
     * @author cdobler
     */
    private static class TransferFixture {

        /*package*/ final Config config;
        /*package*/ final Scenario scenario;
        /*package*/ final TransitSchedule schedule;
        /*package*/ final RaptorConfig routerConfig;

        final TransitStopFacility stop0;
        final TransitStopFacility stop1;
        final TransitStopFacility stop2;
        final TransitStopFacility stop3;

        final ActivityFacility fromFacility;
        final ActivityFacility toFacility;

        /*package*/ TransferFixture(double additionalTransferTime) {
            this.config = ConfigUtils.createConfig();
            this.config.transitRouter().setAdditionalTransferTime(additionalTransferTime);
            this.scenario = ScenarioUtils.createScenario(this.config);
            this.scenario.getConfig().transit().setUseTransit(true);
            this.routerConfig = RaptorUtils.createRaptorConfig(this.scenario.getConfig());
            this.routerConfig.setSearchRadius(500.0);
            this.routerConfig.setBeelineWalkConnectionDistance(100.0);
            this.routerConfig.setBeelineWalkSpeed(1.0); // so the agents can walk the distance in 100 seconds

            // network
            Network network = this.scenario.getNetwork();

            Node node0 = network.getFactory().createNode(Id.create("0", Node.class), new Coord(0, 1000));
            Node node1 = network.getFactory().createNode(Id.create("1", Node.class), new Coord(25000, 1000));
            Node node2 = network.getFactory().createNode(Id.create("2", Node.class), new Coord(50000, 1000));
            network.addNode(node0);
            network.addNode(node1);
            network.addNode(node2);

            Link link0 = network.getFactory().createLink(Id.create("0", Link.class), node0, node1);
            Link link1 = network.getFactory().createLink(Id.create("1", Link.class), node1, node2);
            network.addLink(link0);
            network.addLink(link1);

            // facilities
            ActivityFacilities facilities = this.scenario.getActivityFacilities();

            this.fromFacility = facilities.getFactory().createActivityFacility(Id.create("fromFacility", ActivityFacility.class), new Coord(0, 1102));
            this.toFacility = facilities.getFactory().createActivityFacility(Id.create("toFacility", ActivityFacility.class), new Coord(50000, 898));
            facilities.addActivityFacility(this.fromFacility);
            facilities.addActivityFacility(this.toFacility);

            // schedule
            this.schedule = this.scenario.getTransitSchedule();
            TransitScheduleFactory sb = this.schedule.getFactory();

            this.stop0 = sb.createTransitStopFacility(Id.create("0", TransitStopFacility.class), new Coord(0, 1002), false);
            this.stop1 = sb.createTransitStopFacility(Id.create("1", TransitStopFacility.class), new Coord(25000,  1002), false);
            this.stop2 = sb.createTransitStopFacility(Id.create("2", TransitStopFacility.class), new Coord(25000, 998), false);
            this.stop3 = sb.createTransitStopFacility(Id.create("3", TransitStopFacility.class), new Coord(50000, 998), false);
            this.schedule.addStopFacility(this.stop0);
            this.schedule.addStopFacility(this.stop1);
            this.schedule.addStopFacility(this.stop2);
            this.schedule.addStopFacility(this.stop3);
            this.stop0.setLinkId(link0.getId());
            this.stop1.setLinkId(link0.getId());
            this.stop2.setLinkId(link1.getId());
            this.stop3.setLinkId(link1.getId());

            // route from 0 to 1
            {
                TransitLine line0to1 = sb.createTransitLine(Id.create("0to1", TransitLine.class));
                this.schedule.addTransitLine(line0to1);
                NetworkRoute netRoute = new LinkNetworkRouteImpl(link0.getId(), link0.getId());
                List<Id<Link>> routeLinks = new ArrayList<>();
                netRoute.setLinkIds(link0.getId(), routeLinks, link0.getId());
                List<TransitRouteStop> stops = new ArrayList<>();
                stops.add(sb.createTransitRouteStop(this.stop0, Time.UNDEFINED_TIME, 0.0));
                stops.add(sb.createTransitRouteStop(this.stop1, 5*60.0, Time.UNDEFINED_TIME));
                TransitRoute route = sb.createTransitRoute(Id.create("0to1", TransitRoute.class), netRoute, stops, "train");
                line0to1.addRoute(route);

                route.addDeparture(sb.createDeparture(Id.create("l0to1 d0", Departure.class), 8.0*3600));
                route.addDeparture(sb.createDeparture(Id.create("l0to1 d1", Departure.class), 9.0*3600));
                route.addDeparture(sb.createDeparture(Id.create("l0to1 d2", Departure.class), 10.0*3600));
                route.addDeparture(sb.createDeparture(Id.create("l0to1 d3", Departure.class), 11.0*3600));
                route.addDeparture(sb.createDeparture(Id.create("l0to1 d4", Departure.class), 12.0*3600));
            }

            // route from 2 to 3
            {
                TransitLine line2to3 = sb.createTransitLine(Id.create("2to3", TransitLine.class));
                this.schedule.addTransitLine(line2to3);
                NetworkRoute netRoute = new LinkNetworkRouteImpl(link1.getId(), link1.getId());
                List<Id<Link>> routeLinks = new ArrayList<>();
                netRoute.setLinkIds(link1.getId(), routeLinks, link1.getId());
                List<TransitRouteStop> stops = new ArrayList<>();
                stops.add(sb.createTransitRouteStop(this.stop2, Time.UNDEFINED_TIME, 0.0));
                stops.add(sb.createTransitRouteStop(this.stop3, 5*60.0, Time.UNDEFINED_TIME));
                TransitRoute route = sb.createTransitRoute(Id.create("2to3", TransitRoute.class), netRoute, stops, "train");
                line2to3.addRoute(route);

                route.addDeparture(sb.createDeparture(Id.create("l2to3 d0", Departure.class), 8.0*3600 + 15 * 60));
                route.addDeparture(sb.createDeparture(Id.create("l2to3 d1", Departure.class), 9.0*3600 + 15 * 60));
                route.addDeparture(sb.createDeparture(Id.create("l2to3 d2", Departure.class), 10.0*3600 + 15 * 60));
                route.addDeparture(sb.createDeparture(Id.create("l2to3 d3", Departure.class), 11.0*3600 + 15 * 60));
                route.addDeparture(sb.createDeparture(Id.create("l2to3 d4", Departure.class), 12.0*3600 + 15 * 60));
            }
        }
    }


    /**
     * Generates the following network for testing:
     * <pre>
     *  [s] stop facilities
     *   l  lines
     *
     *  [2]---3---[3]---3---[4]
     *   |         |
     *   1         2
     *   |         |
     *  [1]---0---[0]
     *
     * </pre>
     *
     * 5 stop facilities and 4 lines:
     * - line 0 from Stop 0 to Stop 1
     * - line 1 from Stop 1 to Stop 2
     * - line 2 from Stop 0 to Stop 3
     * - line 3 from Stop 2 via Stop 3 to Stop 4
     *
     * travel times between stops are always 5 minutes.
     *
     * Lines 0, 1, 3 depart regularly during the day. Line 2 runs only in the night, when
     * the others don't run anymore.
     *
     * When searching a route from [0] to [4], stop [3] is reached with fewer transfers but later.
     * Raptor might have a special case when stop [3] is reached the first time after any departure at this stop,
     * so let's test it that it's handled correctly.
     */
    private static class NightBusFixture {

        /*package*/ final Config config;
        /*package*/ final Scenario scenario;
        /*package*/ final TransitSchedule schedule;
        /*package*/ final RaptorConfig routerConfig;

        final TransitStopFacility stop0;
        final TransitStopFacility stop1;
        final TransitStopFacility stop2;
        final TransitStopFacility stop3;
        final TransitStopFacility stop4;

        Id<TransitLine> lineId0 = Id.create(0, TransitLine.class);
        Id<TransitLine> lineId1 = Id.create(1, TransitLine.class);
        Id<TransitLine> lineId2 = Id.create(2, TransitLine.class);
        Id<TransitLine> lineId3 = Id.create(3, TransitLine.class);

        private NightBusFixture() {
            this.config = ConfigUtils.createConfig();
            this.scenario = ScenarioUtils.createScenario(this.config);
            this.routerConfig = RaptorUtils.createRaptorConfig(this.scenario.getConfig());

            // schedule
            this.schedule = this.scenario.getTransitSchedule();
            TransitScheduleFactory sb = this.schedule.getFactory();

            Id<Link> linkId0 = Id.create(0, Link.class);
            Id<Link> linkId10 = Id.create(10, Link.class);
            Id<Link> linkId20 = Id.create(20, Link.class);
            Id<Link> linkId30 = Id.create(30, Link.class);
            Id<Link> linkId40 = Id.create(40, Link.class);

            this.stop0 = sb.createTransitStopFacility(Id.create("0", TransitStopFacility.class), new Coord(5000, 1000), false);
            this.stop1 = sb.createTransitStopFacility(Id.create("1", TransitStopFacility.class), new Coord(1000, 1000), false);
            this.stop2 = sb.createTransitStopFacility(Id.create("2", TransitStopFacility.class), new Coord(1000, 5000), false);
            this.stop3 = sb.createTransitStopFacility(Id.create("3", TransitStopFacility.class), new Coord(5000, 5000), false);
            this.stop4 = sb.createTransitStopFacility(Id.create("4", TransitStopFacility.class), new Coord(9000, 5000), false);
            this.schedule.addStopFacility(this.stop0);
            this.schedule.addStopFacility(this.stop1);
            this.schedule.addStopFacility(this.stop2);
            this.schedule.addStopFacility(this.stop3);
            this.schedule.addStopFacility(this.stop4);
            this.stop0.setLinkId(linkId0);
            this.stop1.setLinkId(linkId10);
            this.stop2.setLinkId(linkId20);
            this.stop3.setLinkId(linkId30);
            this.stop4.setLinkId(linkId40);

            { // line 0
                TransitLine line0 = sb.createTransitLine(this.lineId0);
                this.schedule.addTransitLine(line0);
                NetworkRoute netRoute = new LinkNetworkRouteImpl(linkId0, linkId10);
                List<TransitRouteStop> stops = new ArrayList<>();
                stops.add(sb.createTransitRouteStop(this.stop0, Time.UNDEFINED_TIME, 0.0));
                stops.add(sb.createTransitRouteStop(this.stop1, 5*60.0, Time.UNDEFINED_TIME));
                TransitRoute route = sb.createTransitRoute(Id.create("0to1", TransitRoute.class), netRoute, stops, "train");
                line0.addRoute(route);

                route.addDeparture(sb.createDeparture(Id.create("l0 d0", Departure.class), 8.0*3600));
            }
            { // line 1
                TransitLine line1 = sb.createTransitLine(this.lineId1);
                this.schedule.addTransitLine(line1);
                NetworkRoute netRoute = new LinkNetworkRouteImpl(linkId10, linkId20);
                List<TransitRouteStop> stops = new ArrayList<>();
                stops.add(sb.createTransitRouteStop(this.stop1, Time.UNDEFINED_TIME, 0.0));
                stops.add(sb.createTransitRouteStop(this.stop2, 5*60.0, Time.UNDEFINED_TIME));
                TransitRoute route = sb.createTransitRoute(Id.create("1to2", TransitRoute.class), netRoute, stops, "train");
                line1.addRoute(route);

                route.addDeparture(sb.createDeparture(Id.create("l1 d0", Departure.class), 8.0*3600 + 10*60));
            }
            { // line 2
                TransitLine line2 = sb.createTransitLine(this.lineId2);
                this.schedule.addTransitLine(line2);
                NetworkRoute netRoute = new LinkNetworkRouteImpl(linkId10, linkId20);
                List<TransitRouteStop> stops = new ArrayList<>();
                stops.add(sb.createTransitRouteStop(this.stop0, Time.UNDEFINED_TIME, 0.0));
                stops.add(sb.createTransitRouteStop(this.stop3, 5*60.0, Time.UNDEFINED_TIME));
                TransitRoute route = sb.createTransitRoute(Id.create("0to3", TransitRoute.class), netRoute, stops, "train");
                line2.addRoute(route);

                route.addDeparture(sb.createDeparture(Id.create("l2 d0", Departure.class), 23.0*3600));
            }
            { // line 3
                TransitLine line3 = sb.createTransitLine(this.lineId3);
                this.schedule.addTransitLine(line3);
                NetworkRoute netRoute = new LinkNetworkRouteImpl(linkId10, linkId20);
                List<TransitRouteStop> stops = new ArrayList<>();
                stops.add(sb.createTransitRouteStop(this.stop2, Time.UNDEFINED_TIME, 0.0));
                stops.add(sb.createTransitRouteStop(this.stop3, Time.UNDEFINED_TIME, 5*60.0));
                stops.add(sb.createTransitRouteStop(this.stop4, 10*60.0, Time.UNDEFINED_TIME));
                TransitRoute route = sb.createTransitRoute(Id.create("2to4", TransitRoute.class), netRoute, stops, "train");
                line3.addRoute(route);

                route.addDeparture(sb.createDeparture(Id.create("l3 d0", Departure.class), 8.0*3600 + 20*60));
            }
        }
    }
}