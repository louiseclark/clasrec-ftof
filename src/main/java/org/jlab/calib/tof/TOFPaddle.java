/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.calib.tof;

import org.jlab.clas.detector.DetectorDescriptor;

/**
 *
 * @author gavalian
 */
public class TOFPaddle {
    
    private static final int LEFT = 0;
    private static final int RIGHT = 1;

	private DetectorDescriptor desc = new DetectorDescriptor();
    
    public int ADCL = 0;
    public int ADCR = 0;
    public int TDCL = 0;
    public int TDCR = 0;
    public float XPOS = 0; 
    public float YPOS = 0; 
    
    public TOFPaddle(int sector, int layer, int paddle){
        this.desc.setSectorLayerComponent(sector, layer, paddle);
    }
    
    public TOFPaddle(int sector, int layer, int paddle,
            int adcL, int adcR, int tdcL, int tdcR){
        this.desc.setSectorLayerComponent(sector, layer, paddle);
        this.ADCL = adcL;
        this.ADCR = adcR;
        this.TDCL = tdcL;
        this.TDCR = tdcR;
    }

    public TOFPaddle(int sector, int layer, int paddle,
            int adcL, int adcR, int tdcL, int tdcR,
            float xpos, float ypos){
        this.desc.setSectorLayerComponent(sector, layer, paddle);
        this.ADCL = adcL;
        this.ADCR = adcR;
        this.TDCL = tdcL;
        this.TDCR = tdcR;
        this.XPOS = xpos;
        this.YPOS = ypos;
    }
    
    
    public final void setData(int adcL, int adcR, int tdcL, int tdcR, float xpos, float ypos){
        this.ADCL = adcL;
        this.ADCR = adcR;
        this.TDCL = tdcL;
        this.TDCR = tdcR;
        this.XPOS = xpos;
        this.YPOS = ypos;
    }
    
    public double geometricMean(){
        return Math.sqrt(ADCL*ADCR);
    }
    
    public double logRatio(){
    	return Math.log((double)ADCR/(double)ADCL);
    }
    
    public boolean isValidLogRatio() {
    	// only if geometric mean is over a minimum
		// only if both TDCs are non-zero - otherwise ADCs are equal and log ratio is always 0
    	//return (this.geometricMean() > 500.0) && (TDCL != 0) && (TDCR != 0);
    	return (this.geometricMean() > 500.0) && (ADCR != ADCL);
    }    
    
    public boolean includeInTimeWalk() {
    	// return true if x and y value is in certain range depending on paddle number
    	// and ADC is positive (only needed for testing where I'm manually subtracting the pedestal
    	// hard coded for s1p10
    	// set cutx and findrange functions in c++
    	int paddle = this.getDescriptor().getComponent() -1;
    	float x = this.XPOS;
    	return !(paddle<24 && x>62.8+13.787*paddle-5 && x<62.8+13.787*paddle+5)
    			&& (this.ADCL- getPedestalL() > 0.1 && this.ADCR - getPedestalR() > 0.1)
    			&& (this.YPOS > -85 && this.YPOS < 85);
    	
    }
    
    public double getTWTimeL() {
    	// uses values from Haiyun's caldb test files
    	// hardcoded for S1 P10 as that's the data I have
    	double c1=-0.0981149;
    	double c0=433.845;
    	return c0+c1*this.TDCL;
    }
    
    public double getTWTimeR() {
    	// uses values from Haiyun's caldb test files
    	// hardcoded for S1 P10 as that's the data I have
    	double c1=-0.0981185;
    	double c0=437.063;
    	return c0+c1*this.TDCR;
    }
    
    public double getPedestalL() {
    	// only needed for test data
    	// real data will have pedestal subtracted
    	// uses values from Haiyun's caldb test files
    	// hardcoded for S1 P10 as that's the data I have
    	return 442.0;
    }

    public double getPedestalR() {
    	// only needed for test data
    	// real data will have pedestal subtracted
    	// uses values from Haiyun's caldb test files
    	// hardcoded for S1 P10 as that's the data I have
    	return 410.0;
    }

    
	private double veffOffset() {
		return 0.0; // get from calibration database, store locally to save going to database for every event
	}
	
	private double veff() {
		return 16.0; // get from calibration database, store locally to save going to database for every event
	}    
    
	public double[] timeResiduals(double[] lambda, double[] order) {
		double[] tr = {0.0, 0.0};
		
		double timeL = getTWTimeL();
		double timeR = getTWTimeR();
		
		timeL = timeL - veffOffset();
		timeR = timeR + veffOffset();
		
		double timeLCorr = timeL + (lambda[LEFT]/Math.pow(ADCL, order[LEFT]));
		double timeRCorr = timeR + (lambda[RIGHT]/Math.pow(ADCR, order[RIGHT]));
		
		tr[LEFT] = ((timeL - timeRCorr)/2) - (YPOS/veff());
		tr[RIGHT] =  - ((timeLCorr - timeR)/2) + (YPOS/veff());
		
		return tr;
	}      
    
    public double leftRight() {
    	double timeLeft=tdcToTime(TDCL);
		double timeRight=tdcToTime(TDCR);
		double vEff = 16; // default effective velocity to 16cm/ns
		return (timeLeft-timeRight)*vEff;
    }
    
    public boolean isValidLeftRight() {
    	return (tdcToTime(TDCL) != tdcToTime(TDCR));
    }
    
    double tdcToTime(double value){
    	double c1=0.0009811; // average value from CLAS
    	double c0=0;
    	return c0+c1*value;
    }		
    
    public double position() {
		double vEff = 16; // default effective velocity to 16cm/ns
		return ((tdcToTime(TDCL)-tdcToTime(TDCR))*vEff)/2.0;
    }
    

    public DetectorDescriptor getDescriptor(){ return this.desc;}
    
    public String toString() {
    	return "S " + desc.getSector() + " L " + desc.getLayer() + " C " + desc.getComponent() +
    		   " ADCR " + ADCR +
    		   " ADCL " + ADCL +
    		   " TDCR " + TDCR +
    		   " TDCL " + TDCL +
    		   " Geometric Mean " + geometricMean() +
    		   " Log ratio " + logRatio();
    }
    
    
}
