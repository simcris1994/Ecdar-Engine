package models;

import global.LibLoader;
import lib.DBMLib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Zone {
    private int[] dbm;
    private int size;
    private int actualSize;
    private static final int DBM_INF = Integer.MAX_VALUE - 1;

    public Zone(int size, boolean delay) {
        this.size = size;
        this.actualSize = size * size;

        LibLoader.load();
        int[] temp = new int[actualSize];

        // zone for initial state is dbm_zero with delay
        this.dbm = DBMLib.dbm_zero(temp, size);
        if(delay) delay();
    }

    public Zone(int[] dbm) {
        this.dbm = dbm.clone();
        this.size = (int) Math.sqrt(dbm.length);
        this.actualSize = dbm.length;

        LibLoader.load();
    }

    // copy constructor
    public Zone(Zone oldZone) {
        this.size = oldZone.size;
        this.actualSize = oldZone.actualSize;
        this.dbm = oldZone.dbm.clone();

        LibLoader.load();
    }

    public int getSize() {
        return size;
    }

    public int getElementAt(int i) {
        return dbm[i];
    }

    public int[] getZoneValues() {
        int[] newZone = new int[actualSize];

        for (int i = 0; i < actualSize; i++) {
            newZone[i] = DBMLib.raw2bound(dbm[i]);
        }

        return newZone;
    }

    public int getMaxRawDelay() {
        int max = DBM_INF;

        for (int i = 1; i < size; i++) {
            // upper bound - lower bound of corresponding clock
            int ub = dbm[size * i];
            int lb = dbm[i];
            int diff = DBMLib.dbm_addRawRaw(ub, lb);
            if (diff < max) max = diff;
        }

        return max;
    }

    public void buildConstraintsForGuard(Guard g, int index) {
        boolean isStrict = g.isStrict();

        int lowerBoundI = g.getLowerBound();
        int upperBoundI = g.getUpperBound();

        // if guard is of type "clock == value"
        if (lowerBoundI == upperBoundI) {
            constrain1(0, index, (-1) * lowerBoundI, isStrict);
            constrain1(index, 0, upperBoundI, isStrict);
        }

        if (upperBoundI == Integer.MAX_VALUE)
            constrain1(0, index, (-1) * lowerBoundI, isStrict);

        if (lowerBoundI == 0)
            constrain1(index, 0, upperBoundI, isStrict);
    }

    public void updateValue(int index, int value) {
        dbm = DBMLib.dbm_updateValue(dbm, size, index, value);
    }

    public void delay() {
        dbm = DBMLib.dbm_up(dbm, size);
    }

    public void extrapolateMaxBounds(int constant){
        int[] maxBounds = new int[size];
        if(constant != 0)
        Arrays.fill(maxBounds, 1, size, constant);

        dbm = DBMLib.dbm_extrapolateMaxBounds(dbm, size, maxBounds);
    }

    public Zone getAbsoluteZone(List<Guard> guards, List<Clock> clocks) {
        int[] result = dbm;
        boolean isStrict;
        List<Integer> clockIndices = IntStream.rangeClosed(1, size - 1).boxed().collect(Collectors.toList());

        for (Guard guard : guards) {
            int index = clocks.indexOf(guard.getClock()) + 1;
            isStrict = guard.isStrict();
            boolean firstVisit = clockIndices.contains(index);

            // Get corresponding UBs (UpperBounds) and LBs (LowerBounds)
            int guardUB = guard.getUpperBound();
            int rawClockUB = dbm[size * index];
            int rawClockLB = dbm[index];
            int constraint, rawConstraint;

            if (firstVisit) clockIndices.remove(new Integer(index));
            if (firstVisit && rawClockLB != 1) {
                result = DBMLib.dbm_freeDown(result, size, index);

            }
            // If guard is GEQ:
            if (guardUB == Integer.MAX_VALUE) {
                constraint = guard.getLowerBound();
                rawConstraint = DBMLib.boundbool2raw(constraint, isStrict);

                int rawNewLB = rawConstraint - rawClockLB < 0 ? 1 : DBMLib.dbm_addRawRaw(rawConstraint, rawClockLB);
                isStrict = DBMLib.dbm_rawIsStrict(rawNewLB);
                int newLB = DBMLib.raw2bound(rawNewLB);

                result = DBMLib.dbm_constrainBound(result, size, 0, index, (-1) * newLB, isStrict);

                if (firstVisit && rawClockUB != DBM_INF) {
                    int rawNewUB = DBMLib.dbm_addRawRaw(rawClockUB, rawClockLB);
                    isStrict = DBMLib.dbm_rawIsStrict(rawNewUB);
                    int newUB = DBMLib.raw2bound(rawNewUB);

                    result = DBMLib.dbm_constrainBound(result, size, index, 0, newUB, isStrict);
                }


            } else {
                // If guard is LEQ:
                constraint = guardUB;
                rawConstraint = DBMLib.boundbool2raw(constraint, isStrict);

                int rawNewUB = rawConstraint > rawClockUB && rawClockUB != DBM_INF ?
                        DBMLib.dbm_addRawRaw(rawClockUB, rawClockLB) :
                        DBMLib.dbm_addRawRaw(rawConstraint, rawClockLB);

                isStrict = DBMLib.dbm_rawIsStrict(rawNewUB);

                int newUB = DBMLib.raw2bound(rawNewUB);

                result = DBMLib.dbm_constrainBound(result, size, index, 0, newUB, isStrict);
            }
        }

        // After processing all guards we have to update clocks that had no related guards
        for (Integer index : clockIndices) {

            int clockUB = dbm[size * index];
            int clockLB = dbm[index];

            if (clockLB != 1) {
                result = DBMLib.dbm_freeDown(result, size, index);

                if (clockUB != DBM_INF) {
                    int rawNewUB = DBMLib.dbm_addRawRaw(clockUB, clockLB);
                    isStrict = DBMLib.dbm_rawIsStrict(rawNewUB);
                    int newUB = DBMLib.raw2bound(rawNewUB);
                    result = DBMLib.dbm_constrainBound(result, size, index, 0, newUB, isStrict);
                }
            }
        }
        return new Zone(result);
    }

    public boolean containsNegatives() {
        if (size > 2) {
            for (int i = size; i < actualSize; i++) {
                if (dbm[i] <= 0) return true;
            }
        }
        return false;
    }

    public boolean absoluteZonesIntersect(Zone zone) {
        int[] values1 = getRawRowMaxColumnMin();
        int[] values2 = zone.getRawRowMaxColumnMin();

        int raw1 = DBMLib.dbm_addRawRaw(values1[0], values2[1]);
        int raw2 = DBMLib.dbm_addRawRaw(values2[0], values1[1]);

        return raw1 > 0 && raw2 > 0;
    }

    private int[] getRawRowMaxColumnMin() {
        int rowMax = 1;
        int columnMin = DBM_INF;

        for (int i = 1; i < size; i++) {
            int curr = dbm[i];
            if (curr < rowMax) rowMax = curr;

            curr = dbm[size * i];
            if (curr < columnMin) columnMin = curr;
        }

        return new int[]{rowMax, columnMin};
    }

    public void updateLowerBounds(Zone prevZone, int rawRowMax) {

        for (int i = 1; i < size; i++) {

            int prevRawValue = prevZone.getElementAt(i);
            int currRawValue = dbm[i];
            int targetRawValue = DBMLib.dbm_addRawRaw(prevRawValue, rawRowMax);
            if (currRawValue != targetRawValue) {
                boolean isStrict = DBMLib.dbm_rawIsStrict(targetRawValue);

                dbm = DBMLib.dbm_constrainBound(dbm, size, 0, i, DBMLib.raw2bound(targetRawValue), isStrict);
            }
        }
    }

    public int getRawRowMax() {
        int rowMax = 1;

        for (int i = 1; i < size; i++) {
            int curr = dbm[i];
            if (curr < rowMax) rowMax = curr;
        }

        return rowMax;
    }

    public int getRawColumnMin() {
        int colMin = DBM_INF;

        for (int i = 1; i < size; i++) {
            int curr = dbm[i * size];
            if (curr < colMin) colMin = curr;
        }

        return colMin;
    }

    public boolean isSubset(Zone zone2) {
        return DBMLib.dbm_isSubsetEq(this.dbm, zone2.dbm, size);
    }

    public boolean isValid() {
        return DBMLib.dbm_isValid(dbm, size);
    }

    // This zone and received zone MUST BE OF THE SAME SIZE!!!
    public boolean intersects(Zone zone){
        if(this.size != zone.size) throw new IllegalArgumentException("Zones must be of the same size");
        return DBMLib.dbm_intersection(dbm, zone.dbm, size);
    }

    public boolean canDelayIndefinitely(){
        for (int i = 1; i < size; i++) {
            int curr = dbm[size * i];
            if (curr < DBM_INF) return false;
        }
        return true;
    }

    public boolean isUrgent(){
        for (int i = 1; i < size; i++) {
            int currLower = dbm[i];
            int currUpper = dbm[size * i];
            if (DBMLib.dbm_addRawRaw(currLower, currUpper) != 1)
                return false;
        }
        return true;
    }

    // Computes timeline from arrival zone given guard zone as a parameter.
    public Zone createTimeline(Zone guardZone){
        int LB, UB, FLB = 1, FUB = DBM_INF;
        for (int i = 1; i < size; i++) {
            LB = DBMLib.dbm_addRawRaw(dbm[size * i], guardZone.getElementAt(i));
            UB = DBMLib.dbm_addRawRaw(dbm[i], guardZone.getElementAt(size * i));

            // LB cannot be a positive bound by definition, therefore it has to be <=0
            if(LB > 1) LB = 1;
            // If UB is a negative bound (semantics of lower bound), then arrZone and guardZone never intersect.
            if(UB < 1) return null;

            if(LB < FLB) FLB = LB;
            if(UB < FUB) FUB = UB;
        }

        return new Zone(new int[]{1, FLB, FUB, 1});
    }

    public void updateArrivalZone(Zone timeline){
        int LB, UB, NLB, NUB;
        for (int i = 1; i < size; i++) {
            LB = dbm[i];
            dbm = DBMLib.dbm_constrainRaw(dbm, size, i, 0, DBMLib.dbm_addRawRaw(LB, timeline.getElementAt(1)));



            dbm = DBMLib.dbm_constrainRaw(dbm, size, 0, i, DBMLib.dbm_addRawRaw(LB, timeline.getElementAt(1)));
        }
    }

    public int lowerBoundToUpperBound(int raw){
        return raw * (-1) + (raw & 1) * 2;
    }

    // FURTHER METHODS ARE ONLY MEANT TO BE USED FOR TESTING. NEVER USE THEM DIRECTLY IN YOUR CODE
    public void constrain1(int i, int j, int constraint, boolean isStrict) {
        dbm = DBMLib.dbm_constrainBound(dbm, size, i, j, constraint, isStrict);
    }

    public void init() {
        dbm = DBMLib.dbm_init(dbm, size);
    }

    public int[] getDbm() {
        return dbm;
    }

    // Method to nicely print DBM for testing purposes.
    // The boolean flag determines if values of the zone will be converted from DBM format to actual bound of constraint
    public void printDBM(boolean toConvert, boolean showStrictness) {
        int intLength = 0;
        int toPrint = 0;

        System.out.println("---------------------------------------");
        for (int i = 0, j = 1; i < actualSize; i++, j++) {

            toPrint = toConvert ? DBMLib.raw2bound(dbm[i]) : dbm[i];

            System.out.print(toPrint);

            if (showStrictness) {
                String strictness = DBMLib.dbm_rawIsStrict(dbm[i]) ? " < " : " <=";
                System.out.print(strictness);
            }
            if (j == size) {
                System.out.println();
                if (i == actualSize - 1) System.out.println("---------------------------------------");
                j = 0;
            } else {
                intLength = String.valueOf(toPrint).length();
                for (int k = 0; k < 14 - intLength; k++) {
                    System.out.print(" ");
                }
            }
        }
    }

    public List<Guard> buildGuardsFromZone(List<Clock> clocks) {
        List<Guard> guards = new ArrayList<>();

        for (int i = 1; i < size; i++) {
            Clock clock = clocks.get(i - 1);

            // values from first row, lower bounds
            int lb = dbm[i];
            // lower bound must be different from 1 (==0)
            if (lb != 1) {
                Guard g1 = new Guard(clock, (-1) * DBMLib.raw2bound(lb), true, DBMLib.dbm_rawIsStrict(lb));
                guards.add(g1);
            }
            // values from first column, upper bounds
            int ub = dbm[size*i];
            // upper bound must be different from infinity
            if (ub != DBM_INF) {
                Guard g2 = new Guard(clock, DBMLib.raw2bound(ub), false, DBMLib.dbm_rawIsStrict(ub));
                guards.add(g2);
            }
        }

        return guards;
    }

    @Override
    public String toString() {
        return Arrays.toString(dbm);
    }
}