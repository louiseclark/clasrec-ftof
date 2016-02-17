/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.calib.tof;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.TreeMap;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.jlab.clas.detector.ConstantsTable;
import org.jlab.clas.detector.ConstantsTablePanel;
import org.jlab.clas.detector.DetectorDescriptor;
import org.jlab.clas.detector.DetectorType;
import org.jlab.clas.detector.IConstantsTableListener;
import org.jlab.clas12.calib.CalibrationPane;
import org.jlab.clas12.calib.DetectorShape2D;
import org.jlab.clas12.calib.DetectorShapeView2D;
import org.jlab.clas12.calib.IDetectorListener;
import org.jlab.clas12.detector.DetectorCounter;
import org.jlab.clas12.detector.EventDecoder;
import org.jlab.clas12.detector.FADCBasicFitter;
import org.jlab.clasrec.main.DetectorMonitoring;
import org.jlab.evio.clas12.EvioDataEvent;
import org.jlab.evio.clas12.EvioSource;
import org.root.func.F1D;
import org.root.group.TBrowser;
import org.root.histogram.H1D;
import org.root.pad.EmbeddedCanvas;

/**
 *
 * @author gavalian
 */
public class TOFCalibration implements IDetectorListener,IConstantsTableListener,ActionListener {
    
    private EmbeddedCanvas   		canvas = new EmbeddedCanvas();
    
    private CalibrationPane  		calibPane = new CalibrationPane();
    
    private ConstantsTable   		constantsTable = null;
    private ConstantsTablePanel   	constantsTablePanel = null;

    private TOFHighVoltage hv = new TOFHighVoltage();
    
	public final int GEOMEAN = 0;
	public final int LOGRATIO = 1;    
    
    public static final int[]		NUM_PADDLES = {23,62,5};
    public static final String[]	LAYER_NAME = {"FTOF1A","FTOF1B","FTOF2B"};
    public static final int			ALLOWED_MIP_DIFF = 50;
        
    
    public TOFCalibration(){
        this.initDetector();
        this.init();
    }
    
    public JPanel getView(){ return this.calibPane;}
    
    public void init(){
        this.calibPane.getCanvasPane().add(canvas);
        this.constantsTable = new ConstantsTable(DetectorType.FTOF,
                new String[]{"Geometric Mean Peak","Uncertainty", "Log Ratio Mean", "Uncertainty"});

        this.constantsTablePanel = new ConstantsTablePanel(this.constantsTable);
        this.constantsTablePanel.addListener(this);        
        this.calibPane.getTablePane().add(this.constantsTablePanel);
        
        JButton buttonFit = new JButton("Fit");
        buttonFit.addActionListener(this);
        
        JButton buttonViewAll = new JButton("View all");
        buttonViewAll.addActionListener(this);
        
        this.calibPane.getBottonPane().add(buttonFit);
        this.calibPane.getBottonPane().add(buttonViewAll);
        
        hv.init();
        processFile(hv);
        // Display sector 1 initially
        hv.drawComponent(1, 1, 1, canvas);
        hv.fillTable(1, 1, constantsTable);
        
    }
    
    public void initDetector(){
        
    	for (int layer = 1; layer <= 3; layer++) {
    		int layer_index = layer-1;
    		DetectorShapeView2D view = new DetectorShapeView2D(LAYER_NAME[layer_index]);
    		for(int sector = 1; sector <= 6; sector++){
    			int sector_index = sector -1;
        		for(int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++){
        			
        			int paddle_index = paddle-1;
        			DetectorShape2D  shape = new DetectorShape2D();
                    shape.getDescriptor().setType(DetectorType.FTOF1A);
                    shape.getDescriptor().setSectorLayerComponent(sector, layer, paddle);
                    shape.createBarXY(18, 80 + paddle_index*20);
                    shape.getShapePath().translateXYZ(120+20*paddle_index, 0, 0);
                    shape.getShapePath().rotateZ(Math.toRadians((sector_index*60.0)+180.0));
                    if(paddle%2==0){
                        shape.setColor(180, 255,180);
                    } else {
                        shape.setColor(180, 180, 255);
                    }
                    view.addShape(shape);

        		}
    		}
            view.addDetectorListener(this);
            this.calibPane.getDetectorView().addDetectorLayer(view);
    	}
    }
    /**
     * This method comes from detector listener interface.
     * @param dd 
     */
    public void detectorSelected(DetectorDescriptor dd) {
        
        int sector = dd.getSector();
        int layer =  dd.getLayer();
        int paddle = dd.getComponent();

        hv.drawComponent(sector, layer, paddle, canvas);
        
        // If the sector or layer has changed then redraw the table
        if (sector != Integer.parseInt((String)constantsTable.getValueAt(0, 1)) ||
        	layer != Integer.parseInt((String)constantsTable.getValueAt(0, 2))) {
        	System.out.println("Refilling table with sector " + sector + " layer " + layer );
        	hv.fillTable(sector, layer, constantsTable);
        	constantsTablePanel.repaint();
        }
        
    }

    public void update(DetectorShape2D dsd) {
    	// check any constraints
    	
    	double mipChannel = hv.getCalibrationValue(dsd.getDescriptor().getSector(), 
				   dsd.getDescriptor().getLayer(), 
				   dsd.getDescriptor().getComponent(), hv.GEOMEAN, 1);
    	int layer_index = dsd.getDescriptor().getLayer()-1;
    	double expectedMipChannel = hv.EXPECTED_MIP_CHANNEL[layer_index];
    	
        if (mipChannel < expectedMipChannel - ALLOWED_MIP_DIFF ||
        	mipChannel > expectedMipChannel + ALLOWED_MIP_DIFF) {
        	
        	dsd.setColor(255, 153, 51); // amber
        	
        }
        else if (dsd.getDescriptor().getComponent()%2==0) {
            dsd.setColor(180, 255,180);
        } else {
            dsd.setColor(180, 180, 255);
        }
    	
    }
    
    public void entrySelected(int sector, int layer, int paddle) {

    	hv.drawComponent(sector, layer, paddle, canvas);
        
    }
    
    public static void processFile(TOFHighVoltage hv) {
        // my file with Haiyun's data turned into evio events
    	// use DataProvider for this
    	//String file = "/home/louise/coatjava/FtofInputFile_panel1a1bS6_from_root_file1.evio";
    	
    	// Cole's file - use DataProviderRaw
    	String file = "/home/louise/sector2_000251_mode7.evio.0";
    	
        EvioSource reader = new EvioSource();
        reader.open(file);
        System.out.println(reader.getSize());
        
        EventDecoder decoder = new EventDecoder();
        //decoder.addFitter(DetectorType.FTOF1A, new FADCBasicFitter(30,35,70,75));
        
        int maxEvents = 0;
        int eventNum = 100;
        while(reader.hasEvent()&&(eventNum<maxEvents||maxEvents==0)){
        	EvioDataEvent event = (EvioDataEvent) reader.getNextEvent();
        	hv.processEvent(event, decoder);
        	eventNum++;
        }

        hv.analyze();

    }
    public void actionPerformed(ActionEvent e) {
        System.out.println("ACTION PERFORMED : " + e.getActionCommand());
        if(e.getActionCommand().compareTo("Fit")==0){
        	
        	int sector = constantsTablePanel.getSelected().getSector();
        	int layer = constantsTablePanel.getSelected().getLayer();
        	int paddle = constantsTablePanel.getSelected().getComponent();
	    	
        	hv.customFit(sector, layer, paddle);
	    	F1D f = hv.getF1D(sector, layer, paddle)[GEOMEAN];
	    	
	    	constantsTable.getEntry(sector, layer, paddle).setData(0, Math.round(f.getParameter(1)));
			constantsTablePanel.repaint();
			
			hv.drawComponent(sector, layer, paddle, canvas);
			calibPane.repaint();
			
            
        }
        else if (e.getActionCommand().compareTo("View all")==0){

        	int sector = constantsTablePanel.getSelected().getSector();
        	int layer = constantsTablePanel.getSelected().getLayer();

        	JFrame viewAllFrame = new JFrame();
        	viewAllFrame.add(hv.showFits(sector, layer));
        	viewAllFrame.pack();
        	viewAllFrame.setVisible(true);
        	viewAllFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        	
        }
    }
    
    public static void main(String[] args){
        JFrame frame = new JFrame("FTOF Calibration");
        frame.setSize(1200, 700);
        TOFCalibration calib = new TOFCalibration();
        
        frame.add(calib.getView());
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        
   }

}