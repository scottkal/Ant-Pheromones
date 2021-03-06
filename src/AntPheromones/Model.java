package AntPheromones;

/**
 *  A simple model of bugs in a 2D world, going to an
 *  exogenous source of pheromone pumped into the center cell.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.awt.Point;
import java.io.PrintWriter;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;

import uchicago.src.sim.engine.Schedule;
import uchicago.src.sim.space.Diffuse2D;
import uchicago.src.sim.util.SimUtilities;


public class Model extends ModelParameters {

	// instance variables for run-time parameters
	public int 				numAnts = 60;         // initial number of ants
    public int              sizeX = 100, sizeY = 100;   // integer size of the world 
	public double			maxAntWeight = 10.0;   // max initial weight
	public int				numFoods = 3;			// initial food piles
	
	public double			diffusionK = 0.90;	// for the pheromone space
	public double			evapRate = 1.00; 	// this is really "inverse" of rate! 
	public int			    maxPher = 32000;    // max value, so we can map to colors
	public int 			    pSourceX, pSourceY; // exogenous source of pheromone
	public double			exogRate = 0.30;   	// exog source rate, frac  of maxPher
	public int				initialSteps = 100; // pump in exog pher, diff, this # stpes
	
	// instance variables for model "structures"
	public ArrayList<Ant>   antList = new ArrayList<Ant> ();
	public ArrayList<Food>  foodList = new ArrayList<Food> ();
	public TorusWorld	    world;         	// 2D class built over Repast
	public Diffuse2D  	   	pSpace;			// a 2d space for pheromones from RePast
	public Diffuse2D        pSpaceCarryingFood;    // a 2d space for pheromones dropped by ants

	public double			probRandMoveMean;   // mean,var of probRandMove
	public double			probRandMoveSD;    // assigned to bugs as created
	public double			probRandMoveMutSD;    // assigned to bugs as created

	public double			probDieCenterMean;  // mean,var of prob of dying in 
	public double			probDieCenterSD;   // center of world (by food!)
	public double			probDieCenterMutSD;   // center of world (by food!)

	public double			bestWinsProb = 0.90; // for tournament selection
	public int				tournamentSize = 2;  // size of tournament
	
	public int				activationOrder;    // control how bug-activation is done
	public static final     int fixedActivationOrder = 0;
	public static final     int rwrActivationOrder = 1;  // random with replacement
	public static final     int rworActivationOrder = 2; // random without replacement

	public int				randomMoveMethod = 0;  // how bugs choose random cell to move to
	
	// instance variables for aggregate measures
	public double			antPopAvgX;  	    // observed avg ant X loc
	public double			antPopAvgDistanceFromSource;
	public double			totalPheromone;
	public double			averageBugNbor1Count; // avg of #bugs d=1 away from each bug
	public double			averageBugNbor2Count; // d=2 away
	
	public double			avgProbRandomMove;	   // the measured value!
	public double			avgProbDieCenter;      // the measured value!
	public int				deathsPerStep;

	public DescriptiveStatistics avgDStats;  // univariate stats on averageDistanceFromSource

	ArrayList<Point> keyPoints = new ArrayList<Point>();  // points to measure distance to

    public String pherReportFileName = ""; 		// for a second report file
    public PrintWriter pherReportFile, pherPlainTextReportFile;
	public int pherReportFrequency = 9999999;  // basically its off 

	// an iv used by repast for keeping track of model steps
	public Schedule   		schedule;			// repast schedule of events

	/////////////////////////////////////////////////////////////////////////////
	// To add a new parameter that can be set at run time (via command line or gui)
	// do the following steps:
	// 0. Add an instance variable (field) for the parameter to Model.java .
	// 1. In the method addModelSpecificParameters
	//    add alias and long name for Model parameters you want to set at run time
	//    the long name should be same as instance variable
	// 2. Add setter and getter methods for the parameter
	// 3. In the method getInitParam, add the parameter name to the array of Strings.
	// 4. Compile and test by running model and seeing that the parameter:
	//    - is on the GUI panel, in the report file, and settable on the command line,
	//      eg     bin/batchrun.sh newParName=1.4
	// Note: the generic parameters from ModelParameters are already available.
	//       enter    bin/guirun.sh -h  
	// to see all parameters and defaults, including new ones you add.

	public void addModelSpecificParameters () {
		parametersMap.put( "X", "sizeX" );
		parametersMap.put( "Y", "sizeY" );
		parametersMap.put( "nA", "numAnts" );
		parametersMap.put( "nF", "numFoods");
		parametersMap.put( "eR", "evapRate" );
		parametersMap.put( "dK", "diffusionK" );
		parametersMap.put( "exogR", "exogRate" );
		parametersMap.put( "prmm", "probRandMoveMean"  );
		parametersMap.put( "prmsd", "probRandMoveSD" );
		parametersMap.put( "prmmsd", "probRandMoveMutSD" );
		parametersMap.put( "prdcm", "probDieCenterMean"  );
		parametersMap.put( "prdcsd", "probDieCenterSD" );
		parametersMap.put( "prdcmsd", "probDieCenterMutSD" );
		parametersMap.put( "ao", "activationOrder" );
		parametersMap.put( "rmm", "randomMoveMethod" );
		parametersMap.put( "bwp", "bestWinsProb" );
		parametersMap.put( "ts", "tournamentSize" );
		parametersMap.put( "pRFN", "pherReportFileName" );
		parametersMap.put( "pRF", "pherReportFrequency" );
		parametersMap.put( "iS", "initialSteps" );
	}

	// Specify what appears in the repast parameter panel
	public String[] getInitParam () {
		String[] params = { "numAnts", "numFoods", "sizeX", "sizeY",
							"evapRate", "diffusionK", "exogRate",
							"probRandMoveMean", "probRandMoveSD", "probRandMoveMutSD",
							"probDieCenterMean", "probDieCenterSD", "probDieCenterMutSD",
							"activationOrder", "randomMoveMethod", 
							"bestWinsProb", "tournamentSize", "pherReportFileName",
							"pherReportFrequency",  "initialSteps",
				// these are from the super class:
				"rDebug", "seed" };
		return params;
	}

	/////////////////////////////////////////////////////////////////////////////
	// setters and getters 
	// These must be defined to be able to set parameters via GUI and run command 
	//
	// NB: some things can't be changed after run starts (eg sizeX,sizeY)

	public int getNumFoods () { return numFoods; }
	public void getNumFoods ( int nmF ) { 
		numFoods = nmF;
	}
	
	public int getNumAnts () { return numAnts; }
	public void setNumAnts ( int nmA ) { 
		numAnts = nmA;
	}
	public int getSizeX () { return sizeX; }
	public void setSizeX ( int szX ) { 
		sizeX = szX; 
	}
	public int getSizeY () { return sizeY; }
	public void setSizeY ( int szY ) { 
		sizeY = szY;  
	}

	//  The following can be changed in mid-run:
	public double getDiffusionK () { return diffusionK; }
	public void setDiffusionK ( double diffusionK ) { 
		this.diffusionK = diffusionK;
		if ( pSpace != null )
			pSpace.setDiffusionConstant( diffusionK );
	}
	public double getEvapRate () { return evapRate; }
	public void setEvapRate ( double evapRate ) { 
		this.evapRate = evapRate;  
		if ( pSpace != null )
			pSpace.setEvaporationRate( evapRate );
	}
	public double getExogRate () { return exogRate; }
	public void setExogRate ( double exogRate ) { 
		this.exogRate = exogRate;  
	}

	public double getProbRandMoveMean () { return probRandMoveMean; }
	public void setProbRandMoveMean ( double probRandMoveMean ) { 
		this.probRandMoveMean = probRandMoveMean;
	}
	public double getProbRandMoveSD () { return probRandMoveSD; }
	public void setProbRandMoveSD ( double probRandMoveSD  ) { 
		this.probRandMoveSD = probRandMoveSD;  
	}
	public double getProbDieCenterMean() {
		return probDieCenterMean;
	}

	public void setProbDieCenterMean(double probDieCenterMean) {
		this.probDieCenterMean = probDieCenterMean;
	}

	public double getProbDieCenterSD() {
		return probDieCenterSD;
	}

	public void setProbDieCenterSD(double probDieCenterSD) {
		this.probDieCenterSD = probDieCenterSD;
	}


	/**
	 * @return the probRandMoveMutSD
	 */
	public double getProbRandMoveMutSD() {
		return probRandMoveMutSD;
	}

	/**
	 * @param probRandMoveMutSD the probRandMoveMutSD to set
	 */
	public void setProbRandMoveMutSD(double probRandMoveMutSD) {
		this.probRandMoveMutSD = probRandMoveMutSD;
	}

	/**
	 * @return the probDieCenterMutSD
	 */
	public double getProbDieCenterMutSD() {
		return probDieCenterMutSD;
	}

	/**
	 * @param probDieCenterMutSD the probDieCenterMutSD to set
	 */
	public void setProbDieCenterMutSD(double probDieCenterMutSD) {
		this.probDieCenterMutSD = probDieCenterMutSD;
	}

	public double getBestWinsProb () { return bestWinsProb; }
	public void setBestWinsProb ( double d  ) { 
		bestWinsProb = d;  
	}
	public int getTournamentSize () { return tournamentSize; }
	public void setTournamentSize ( int t ) { 
		tournamentSize = t;  
	}
	public int getActivationOrder () { return activationOrder; }
	public void setActivationOrder ( int activationOrder ) { 
		if ( (activationOrder != fixedActivationOrder) &&
			  (activationOrder != rwrActivationOrder) &&
			  (activationOrder != rworActivationOrder) ) {
			System.err.printf( "\nIllegal activation Order!\n" );
		}
		this.activationOrder = activationOrder;  
	}

	// Note that randomMoveMethod is also sent to the Ant class,
	// so that they all know the new value when its changed.
	public int getRandomMoveMethod () { return randomMoveMethod ; }
	public void setRandomMoveMethod  ( int randomMoveMethod  ) { 
		this.randomMoveMethod  = randomMoveMethod;
		Ant.setRandomMoveMethod( randomMoveMethod );
	}

	public String getPherReportFileName () { return pherReportFileName; }
	public void setPherReportFileName ( String s ) { pherReportFileName = s; }
	public int getPherReportFrequency () { return pherReportFrequency; }
	public void setPherReportFrequency ( int i ) { pherReportFrequency = i; }
	
	public int getInitialSteps() {
		return initialSteps;
	}

	public void setInitialSteps(int initialSteps) {
		this.initialSteps = initialSteps;
	}

	// getters for aggregate measures
	public int getAntPopSize() { return antList.size(); }
	public double getAntPopAvgX() { return antPopAvgX; }
	public double getAntAvgDistanceFromSource() { 
		return antPopAvgDistanceFromSource; }

	public double getAntPopAvgWeight() { return 0.0; }  	// to be filled in!
	public double getAntPopAvgNumNbors() { return 0.0; } 	// to be filled in!


	/**
	// userSetup()
	// called when user presses the "reset" button.
	// discard any existing model parts, and re-initialize as desired.
	//
	// NB: if you want values entered via the GUI to remain after restart,
	//     do not initialize them here.
	*/
	public void userSetup() {
		if ( rDebug > 0 )
			System.out.printf( "==> userSetup...\n" );

		antList = null;
		foodList = null; // discard old list 
		world = null;                   // get rid of the world object!
		pSpace = null;
		pSpaceCarryingFood = null;
		Ant.resetNextId();				// reset ant ID's to start at 0

		if ( avgDStats != null )
			avgDStats = null;
		
		keyPoints = new ArrayList<Point>();
		
	}

	/**
	// userBuildModel
	// called when model initialized, eg, with Initialize button.
	// create all the objects that constitute the model:
	// - a 2D world
	// - a list of ants, placed at random in the world
	*/
	public void userBuildModel () {
		if ( rDebug > 0 )
			System.out.printf( "==> userBuildModel...\n" );

		antList = new ArrayList<Ant> (); // create new empty list 
		foodList = new ArrayList<Food> ();
		
		// create the 2D grid world of requested size, linked to this model
		world = new TorusWorld( sizeX, sizeY, this );

		createPSpaceAndInjectInitialPheromone();
		for ( int i = 0; i < initialSteps; ++i ) { // repeat to get desired inital state
			injectExogenousPheromoneAndUpdate();
			pSpace.diffuse();
		}

		// tell the Food class about this (Model)and world addresses
		// so that the foods can send messages to them, e.g.,
		// to query the world about cell contents.
		Food.setModel( this );
		Food.setWorld( world );
		
		createFoodsAndAddToWorld();
		
		// tell the Ant class about this (Model) and world addresses
		// so that the ant's can send messages to them, e.g.,
		// to query the world about cell contents.
		Ant.setModel( this );
		Ant.setWorld( world );
		Ant.setPSpace( pSpace );
		Ant.setRandomMoveMethod( randomMoveMethod );

		createAntsAndAddToWorld();

		// create the stats object; calc initial state stats, store
		// avgDStats = DescriptiveStatistics.newInstance();  // old version of cm
		avgDStats = new DescriptiveStatistics();
		avgDStats.setWindowSize( 10 );
		calcStats();
		avgDStats.addValue( antPopAvgDistanceFromSource );
		
		openPheromoneReportFile();		// open second report file

		keyPoints.add( new Point( 0, 0 ) );
		keyPoints.add( new Point( sizeX-1, sizeY-1 ) );
		keyPoints.add( new Point( 0, (int)sizeY/2 ) );
		
		if ( rDebug > 0 )
			System.out.printf( "<==  userbuildModel done.\n" );

	}

	/**
	 * Opens report file for info about pSpace.
	 */
	private void openPheromoneReportFile() {
		if ( pherReportFile != null )
			endReportFile (  pherReportFile );
		if ( pherPlainTextReportFile != null )
			endPlainTextReportFile (  pherPlainTextReportFile );
		if ( pherReportFileName.length() > 0 ) {
			pherReportFile = startReportFile( pherReportFileName );
			pherPlainTextReportFile = startPlainTextReportFile( pherReportFileName );
		}
	}

	/**
	 * create pSpace world, figure out where to inject pheromone,
	 * inject initial amount, also calc max distance to that source cell (from 0,0).
	 */
	private void createPSpaceAndInjectInitialPheromone() {

		// Set up the pheromone space and related fields.
		// create the 2D diffusion space for pheromones, tell bugs about it
		pSpace = new Diffuse2D( diffusionK, evapRate, sizeX, sizeY );
		// set up the location of exogenous source of pheromone
		pSourceX = sizeX/2;
		pSourceY = sizeY/2;
		// lets start the world with some pheromone...
		// more than gets added each step...but not more than maxPher!
		double exogPheromone = 2.0 * maxPher * exogRate;
		double initPher = Math.min( exogPheromone, (double) maxPher );
		pSpace.putValueAt( pSourceX, pSourceY, initPher );
		pSpace.update();   // move from write copy to read copy
		if ( rDebug > 0 )
			System.out.printf( "- userBuildModel: put initPher=%.3f at %d,%d.\n",
				  pSpace.getValueAt(pSourceX,pSourceY), pSourceX, pSourceY );

		calcAndSetMaxDistanceToSource();
	}

	/**
	 * calcs max distance to source of pheromone, tells Ant class about it.
	 */
	private void calcAndSetMaxDistanceToSource() {
		// calculate max distance to pSource, from 0,0, and tell Bug about it.
		double deltaX = pSourceX;
		double deltaY = pSourceY;
		double mdtc = Math.sqrt( (deltaX*deltaX) + (deltaY*deltaY) );
		Ant.setMaxDistanceToCenter( mdtc );
	}

	/**
	// createAntsAndAddToWorld
	// create numAnts ants,add to antList and 
	// add to random locations in world
	//
	// NB: this will be slow if numAnts ~ number of cells in world
	*/
	
	public void createAntsAndAddToWorld ( ) {
		// create the ants, add to world and to the antlist
		for ( int i = 0; i < numAnts; ++i ) {
			Ant ant = createNewAnt();
			if ( world.placeAtRandomLocation( ant ) )  // if added to world...
				antList.add( ant );					  // add to list
			else
				System.out.printf( "\n** World too full (%d) for new ant!\n\n",
								   antList.size() );
		}

	}
	
	/**
	// createFoodsAndAddToWorld
	// create numFoods foods,add to foodList and 
	// add to random locations in world
	// this is making "food piles" or origins of food
	*/
	
	public void createFoodsAndAddToWorld ( ) {
		// create the foods, add to world and to the foodlist
		for ( int i = 0; i < numFoods; ++i ) {
			Food food = createNewFood();
			if ( world.placeAtRandomLocation( food ) )  // if added to world...
				foodList.add( food );					  // add to list
			else
				System.out.printf( "\n** World too full (%d) for new food!\n\n",
								   foodList.size() );
		}

	}

	/**
	// growFoods
	// create food objects around each existing food objects
	// for each existing food object, find open neighbors, place food objects
	// in each open cell, and iterate a set number of times (probably outside of this method)
	*/
	
	public void growFoods ( ) {
   		// find open neighbor cells and return a list of them
	   
		// place food objects at each location on the list

		// add these new food object to foodList
	 
	}

	/**
	 * Again, stolen from Rick.
	 * Create one new Food, with initial values set by draws from
	 * distributions set by model parameters.
	 * @return
	 */
	public Food createNewFood( ) {
		Food food = new Food();
		return food;
	}
	
	/**
	 * Create one new Ant, with initial values set by draws from
	 * distributions set by model parameters.
	 * @return
	 */
	public Ant createNewAnt( ) {
		Ant ant = new Ant();
		double wt = Model.getUniformDoubleFromTo(0.0,1.0) * maxAntWeight;
		ant.setWeight( wt );
		// get a normal sample (repeat until in [0,1])
		double r = getNormalDoubleProb( probRandMoveMean, probRandMoveSD );
		ant.setProbRandMove( r );
		r = getNormalDoubleProb( probDieCenterMean, probDieCenterSD );
		ant.setProbDieCenter( r );
		return ant;
	}
	

	/**
	// step
	//
	// The top of the model's main dynamics.  This defines
	// what happans each time step (eg, user presses Step or Run buttons...).
	// Currently: 
	 * - add new ants if there are fewer than numAnts on list.
	// - we diffuse the pheronome
	// - bugs take a step (move a bit)
	// - bugs age
	// - we pump in some exogenously supplied pheromone
	// - we update the pSpace (put the written values into the read lattice)
	// - stepReport to write stats to the report file.
	//
	// NB: ants activated in same order each step.
	// There are three ways we can choose the order of activation of bugs:
	// fixedActivationOrder - fixed order (same as they were created)
	// rwrActivationOrder   - random with replacement -- pick numBugs each step
	//       bugs could get 0 or > 1 chances per time step!
	// rworActivationOrder  - random without replacement
	//       bugs get exactly 1 chance per time step, in a random order
	 */

	public void step () {
		if ( rDebug > 0 )
			System.out.printf( "==> Model step %.0f:\n", getTickCount() );

		// Kludge for testing: remove first bug on list!
		// removeAntFromModel( antList.get(0) );
		
		generateNewAnts();   // add new bugs as needed

		// diffuse() diffuses from the read matrix (T) and into write (T')
		// *and* it then does an update(), i.e., writes T' into new read T+1
		pSpace.diffuse();
		
		activateAntsToTakeSteps();
		
		for ( Ant ant : antList )   // each agents gets older
			ant.incrementAge(1);

		injectExogenousPheromoneAndUpdate();
		
		stepReport();		// write aggregate measures to report file

   		if ( rDebug > 0 ) {
			System.out.printf( "    -> Measured pheromone at %d,%d is %.3f.\n",
				   pSourceX, pSourceY, pSpace.getValueAt( pSourceX, pSourceY ) );
			System.out.printf( "<== Model step done.\n" );
		}

	}
	
	/**
	 * add bugs if needed, to get back to numAnts total.
	// new bugs are offspring of winner of tournament (fit = low probDieCenter)
	// offspring gets parent's probDieCenter + G(0,probDieCenterSD)
	// offspring gets parent's probRandomMomve + G(0,probRandomMoveSD)
	// add to edges of world.  for now: just add to X=0 edge!
	// if added ok, also add to antList.
	 */
	public void generateNewAnts () {

		for ( int i = antList.size(); i < numAnts; ++i ) {
			Ant offspring = createNewAnt();
			Ant parent = tournametnSelectParent ( tournamentSize );
			
			setOffSpringProbDieCenter( parent, offspring );
			setOffSpringProbRandomMove( parent, offspring );

			if ( !addAntToRandomEdge( offspring ) ) {
				System.err.printf( "==> step %.0f: couldn't find place on edge for new bug!\n",
								   getTickCount() );
				break;
			}
			antList.add( offspring );
			if ( rDebug > 0 )
				System.out.printf( "    - Added new bug at %d,%d.\n",
								   offspring.getX(), offspring.getY() );
		}
		
	}

	/**
	// add the exogenous supply.  be sure not to go over maxPher
	// otherwise the color doesn't work right.
	// *** Note: This assumes the bugs have not altered the PSpace
	//           without updating the pspace read copy!!
	//     (this is adding to the amount it reads from the Read matrix)	
	// then update the pSpace -- move values from the write to read copy
	 */
	private void injectExogenousPheromoneAndUpdate() {
		double v = (maxPher * exogRate) + pSpace.getValueAt(  pSourceX, pSourceY );
		v =  Math.min( v, maxPher );
		pSpace.putValueAt( pSourceX, pSourceY, v );
		pSpace.update();
	}

	/**
	 * removeAntFromModel:
	 * - if removeFromList is true, remove from antList
	 * - remove it from world
	 * - increment the count of deathsPerStep
	 * NB: assumes it is in world at its x,y location!
	 */
	public void removeAntFromModel ( Ant ant, Boolean removeFromList ) {
		if ( removeFromList )
			antList.remove( ant );
		world.putObjectAt( ant.getX(), ant.getY(), null );
		++deathsPerStep;
	}
	
	
	/**
	// tournamentSelectParent
	// returns a "fit" parent as a result of a competition.
	// select candidates at random. 
	// then run a tournament, with higher 'fitness' winning
	// with probability = bestWinsProb 
	// best defined as lowest probDieCenter value
	 * 
	 * @param tournamentSize2
	 * @return
	 */
	private Ant tournametnSelectParent( int tSize ) {
		Ant winner = null;
		ArrayList<Ant> cList = new ArrayList<Ant>();

		// get the contestants (nb: some could be there twice)
		int alistMax = antList.size() - 1;
		for ( int i = 0; i < tSize; ++i ) {
			cList.add (  antList.get( Model.getUniformIntFromTo( 0, alistMax ) ) );
		}
		Collections.sort( cList,   // sort by probDieCenter -- higher first
			  (java.util.Comparator<? super Ant>) new ProbDieCenterComparator() );
		
		
		// select a winner 
		// go down list in fitness order, giving each a chance to win.
		// if we get to the end with no winner, the last guy wins
		for ( Ant candidate : cList ) {
			if ( bestWinsProb > Model.getUniformDoubleFromTo( 0.0, 1.0 ) ) {
				winner = candidate;
				break;
			}
		}
		if ( winner == null ) // the last wins by default!
			winner = cList.get( cList.size()-1 );

		//System.out.printf( "cList:   (winner=%.3f)\n", winner.getProbDieCenter() );
		//for ( Ant candidate : cList ) {
		//	System.out.printf( "cand pdc=%.3f\n", candidate.getProbDieCenter() );
		//}
		
		if ( rDebug > 0 && cList.size() > 1 ) {  // only looks at top 2
			System.out.printf( "tsp: %.3f > %.3f (bwp=%.2f) -> winner = %.3f.\n",
							   cList.get(0).getProbDieCenter(),
							   cList.get(1).getProbDieCenter(), bestWinsProb,
							   winner.getProbDieCenter() );
		}

		return winner;
	}

	/**	 
	 * set offspring's probDieCenter value to parent's value + mutation, ie,
	 * add N(0,probDieCenterSD) to it, retrying until in [0,1].
	 * @param parent
	 * @param offspring
	 */
	private void setOffSpringProbDieCenter(Ant parent, Ant offspring) {
		double d = -1;
		int numTrials = 0, maxTrials = 1024;  // just in case...
		while ( (d < 0.0 || d > 1.0) && numTrials < maxTrials ) {  // get a legal prob
			d = parent.getProbDieCenter();
			d += Model.getNormalDouble( 0, probDieCenterSD );
			++numTrials;
		}
		// what to do if we can't get a mutation in range:
		// this shouldn't happen, but you never know...
		if ( numTrials == maxTrials ) {
			System.err.printf( "\n=>  couldn't get mutation in range.\n" );
			System.err.printf( "  parent probDieCenter = %f.\n", parent.getProbDieCenter() );
			System.err.printf( "  probDieCenterSD = %f \n", probDieCenterMutSD );
			d = 1.0;
		}
			offspring.setProbDieCenter( d );		
	}

	/**
	 * set offspring's probRandomMove value to parent's value + mutation, ie,
	 * add N(0,probRandMoveSD) to it, retrying until in [0,1].
	 * @param parent
	 * @param offspring
	 */
	private void setOffSpringProbRandomMove(Ant parent, Ant offspring) {
		double d = -1;
		int numTrials = 0, maxTrials = 1024;  // just in case...
		
		while ( (d < 0.0 || d > 1.0) && numTrials < maxTrials ) {  // get a legal prob
			d = parent.getProbRandMove();
			d += Model.getNormalDouble( 0, probRandMoveSD );
			++numTrials;
		}
		// what to do if we can't get a mutation in range:
		// this shouldn't happen, but you never know...
		if ( numTrials == maxTrials ) {
			System.err.printf( "\n=> couldn't get mutation in range.\n" );
			System.err.printf( "  parent probRandMove = %f.\n", parent.getProbRandMove() );
			System.err.printf( "  probRandMoveSD = %f \n", probRandMoveMutSD );
			d = 1.0;
		}
			offspring.setProbRandMove( d );		
	}

	/**
	// addBugToRandomEdge
	// add specified bug to randomly selected cell on an "edge", 
	// i.e, x=0 or y = 0 or x=sizeX-1 or y=sizeY-1 
	//
	// FOR NOW, just add to left (x=0) edge!
	 * 
	 * @param bug
	 * @return
	 */
	
	public boolean addAntToRandomEdge ( Ant bug ) {
		int randomX = 0, randomY =  0, maxTrials = 1024, nmTrials = 0;
		boolean added = true; // lets be hopeful!

		boolean top = false;
		if  ( Model.getUniformDoubleFromTo( 0, 1 ) > 0.5 ) {
			top = true;
		}
			
	   	// find a random place that is un-occupied on left edge
	   	do {
	   		if ( top )
			    randomX =  getUniformIntFromTo( 0, world.getSizeX () - 1 );
	   		else
	   			randomY =  getUniformIntFromTo( 0, world.getSizeY () - 1 );
			++nmTrials;
		} while ( world.getObjectAt( randomX, randomY ) != null && nmTrials < maxTrials );

		if ( nmTrials < maxTrials ) {
			world.putObjectAt( randomX, randomY, bug );
			bug.setX( randomX );
			bug.setY( randomY );
		}
		else {
			System.err.printf( "\n ==> addBugToRandomEdge -- no empty cell found!\n\n" );
			added = false;
		}
		return added;
	}
	
	/**
	 * activate bugs in order based on activationOrder parameter.
	 * if ant step() returns false, the ant is dead, so remove it.
	 */
	public void activateAntsToTakeSteps() {
		Boolean live;
		deathsPerStep = 0;
		// activate bugs in user specified order
		if ( activationOrder == fixedActivationOrder ) {
			// now the bugs get a chance to move around
			for ( int i = 0; i < antList.size(); i++ ) {
				Ant aBug = antList.get (i);
				live = aBug.step ();
				if ( !live ) {  // now we kludge a bit to remove from list properly!
					removeAntFromModel ( aBug, true );
					--i;       // THE KLUDGE -- so as not to skip a bug on the list!
				}
			}
		}
		else if (  activationOrder == rwrActivationOrder ) {
			for ( int i = 0; i < antList.size(); i++ ) {
				int r = getUniformIntFromTo( 0, antList.size()-1 );
				Ant aBug = antList.get ( r );
				if ( !aBug.step() ) {   // note we don't need a kludge, since we pick randomly
					removeAntFromModel ( aBug, true );
				}
			}
		}
		else if (  activationOrder == rworActivationOrder ) {
			// here we shuffle the list, then process in order
			SimUtilities.shuffle( antList, uchicago.src.sim.util.Random.uniform );
			Iterator<Ant> bugIter = antList.iterator();
			while ( bugIter.hasNext() ) {
				Ant aBug = bugIter.next();
				if ( !aBug.step() ) { 
					bugIter.remove();  // remove it from list
					removeAntFromModel ( aBug, false );  // false -> its already gone from list
				}
			}
		}

	}

	/**
	// stepReport
	// called each model time step to write out lines that look like: 
    //     timeStep  ...data...data...data...
	// first it calls a method to calculate stats to be written.
	*/
	public void stepReport () {
		if ( rDebug > 0 )
			System.out.printf( "==> Model stepReport %.0f:\n", getTickCount() );

		calcStats();
		
		if ( getTickCount() %  reportFrequency == 0 ) {
			// set up a string with the values to write -- start with time step
			String s = String.format( "%5.0f  ", getTickCount() );

			// Append to String s here to write other data to report lines:

			s += String.format( " %3d ", antList.size()  );
			s += String.format( "  %3d %6.2f", deathsPerStep, antPopAvgX );
			s += String.format( "  %6.3f   %6.3f", antPopAvgDistanceFromSource, avgDStats.getMean()  );
			s += String.format( "   %6.2f   %6.2f", avgProbRandomMove, avgProbDieCenter );

			// write it to the plain text report file, 'flush' buffer to file
			writeLineToPlaintextReportFile( s );
			getPlaintextReportFile().flush();
		}

		if ( pherReportFile != null && getTickCount() % pherReportFrequency == 0 ) {
			String s;  double v;
			s = String.format( "# step %5.0f", getTickCount() );
			writeLineToReportFile( s, pherPlainTextReportFile );
			for ( int x = 0; x < sizeX; ++x ) {
				s = "";
				for ( int y = 0; y < sizeY; ++y ) {
					v = pSpace.getValueAt( x, y );
					s += String.format( " %5.0f", v );
				}
				writeLineToReportFile( s, pherPlainTextReportFile );
			}
			writeLineToReportFile( "# endstep", pherPlainTextReportFile );
		}
		
	}

	///////////////////////////////////////////////////////////////////////////////
	// writeHeaderCommentsToReportFile
	// customize to match what you are writing to the report files in stepReport.
	
	public void writeHeaderCommentsToReportFile () {
		writeLineToPlaintextReportFile( "#                                    Win10   " );
		writeLineToPlaintextReportFile( "#       Num   Num  avg    AveDist   AveDist    Avg      Avg" );
		writeLineToPlaintextReportFile( "# time  Ants  Die  AntX   toSource  toSource  RandMov  PrDieC" );
	}

	/**
	// calcStats
	// calculate various aggregate measures, store in Model fields.
	// currently just calcs:
	// - average ant X location
	// - average distance from source of pheromone
	// - calc total pheromone
	*/
	public void calcStats () {
		antPopAvgX = 0.0;  avgProbRandomMove = 0.0; avgProbDieCenter = 0.0;

		// average X is sort of silly...
		for ( Ant ant : antList ) {
			antPopAvgX += ant.getX();
			avgProbRandomMove += ant.getProbRandMove();
			avgProbDieCenter += ant.getProbDieCenter();
		}
		if ( antList.size() > 1 ) {
			antPopAvgX /= antList.size();
			avgProbRandomMove /= antList.size();
			avgProbDieCenter /= antList.size();
		}
		// calculate average bug distance from pheromone source
		antPopAvgDistanceFromSource = calcAvgAntPopDistanceTo( pSourceX, pSourceY );
		// record some stats every step
		avgDStats.addValue( antPopAvgDistanceFromSource );
		
		// get total pheromone in pSpace
		totalPheromone = 0;
		for ( int x = 0; x < sizeX; ++x ) {
			for ( int y = 0; y < sizeY; ++y ) {
				totalPheromone += pSpace.getValueAt( x, y );
			}
		}

		// calc avg number of neighbors each bug has, 1 and 2 away
		double totalNbor1Count = 0.0, totalNbor2Count = 0.0;
		for ( Ant aBug : antList ) {
			totalNbor1Count += aBug.getNumberOfNeighbors( 1 );
			totalNbor2Count += aBug.getNumberOfNeighbors( 2 );
		}
	    if ( antList.size() > 1 ) {
			averageBugNbor1Count = totalNbor1Count / antList.size();
			averageBugNbor2Count = totalNbor2Count / antList.size();
		}
	}

	public double calcAvgAntPopDistanceTo ( int x, int y ) {
		double avgD = 0.0, distance, bugX, bugY, deltaX, deltaY;
		for ( Ant bug : antList ) {
			bugX = (double) bug.getX();
			bugY = (double) bug.getY();
			deltaX = bugX - x;
			deltaY = bugY - y;
			distance = Math.sqrt( (deltaX*deltaX) + (deltaY*deltaY) );
			avgD += distance;
		}
		if ( antList.size() > 1 ) 
			avgD /= antList.size();
		return avgD;
	}

	/**
	// printBugs
	// print some info about bugs.
	// NOTE: we sort them first, by distance to source.
	*/
	//@SuppressWarnings("unchecked") 
	public void printBugs ( ) {
		// sort the bugs based on distance to source
		Collections.sort( antList, 
			  (java.util.Comparator<? super Ant>) new BugDistanceToSourceComparator() );
 		//Collections.sort( antList, 
		//      (java.util.Comparator<? super Ant>) new BugWeightComparator() );

		System.out.printf( "\n antList:\n" );
		System.out.printf( "   ID     X   Y   DistToSource    prRndMove\n" ); 
		for ( Ant aBug: antList ) {
			System.out.printf( "  %3d   %3d %3d   %.3f         %.2f\n",
			   	   aBug.getId(), aBug.getX(), aBug.getY(), 
			   	   calcDistanceToSource(aBug), aBug.getProbRandMove() );
		}
		calcStats();  // just in case...
		System.out.printf( " avgDistToSource: %.3f\n", antPopAvgDistanceFromSource );
		System.out.printf( "\n" );
	}

	/**
	// calcDistanceToSource aBug
	// does just that, returns the distance
	 * 
	 * @param aBug
	 * @return
	 */
	double calcDistanceToSource ( Ant aBug ) {
		double bugX, bugY, deltaX, deltaY, distance;
		bugX = (double) aBug.getX();
		bugY = (double) aBug.getY();
		deltaX = bugX - pSourceX;
		deltaY = bugY - pSourceY;
		distance = Math.sqrt( (deltaX*deltaX) + (deltaY*deltaY) );
		return distance;
	}

	/**
	// resetBugProbRandMove
	// reassign probRandMove value to bugs, using values
	// drawn from G(probRandMoveMean, probRandMoveSD )
	//
	 */
	public void resetBugProbRandMove () {
		for ( Ant aBug : antList ) {
			double r = getNormalDoubleProb( probRandMoveMean, probRandMoveSD );
			aBug.setProbRandMove( r );
		}
	}

	/**
	// printPSpaceValues
	// for debugging
	 * 
	 */
    public void printPSpaceValues () {
		for ( int x = 0; x < sizeX; ++x ) {
			for ( int y = 0; y < sizeY; ++y ) {
				double v = pSpace.getValueAt( x, y );
				if ( v > 0 )
					System.out.println( "x,y=v " + x + "," + y + "=" + v );
			}
		}
	}

	///////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////
	// Comparators for sorting
	//

    /**
	// BugDistanceToSourceComparator
	// Callback for sort, to order by ascending distance to source of pheromone
	// for any pair of objects a and b to be sorted:
	// return +1  if a should sort after b, 
	//         0  if a == b,
	//        -1  if a should sort before b
	 * Note this could be cleaned up to accept some set of objects wider than Ant,
	 * eg we could define an interface that guarantees getDistanceToSource() will exist.
	*/
	private class BugDistanceToSourceComparator implements Comparator<Ant> {
		public  int compare ( Ant bug1, Ant bug2 ) {
			double d1 = ( (Ant) bug1 ).getDistanceToSource();
			double d2 = ( (Ant) bug2 ).getDistanceToSource();
			if ( d1 > d2 )    // d2 smaller, so should be before d1 (ascending order)
				return 1;
			else if ( d1 < d2 )
				return -1;
			return 0;
		}
	}
	/**
	 *  we want to sort ants so that lower probDieCenter is first,
	 *  because lower probDieCenter is "better" (more fit)
	 */
	private class ProbDieCenterComparator implements Comparator<Ant> {
		public  int compare ( Ant bug1, Ant bug2 ) {
			double d1 = ( (Ant) bug1 ).getProbDieCenter();
			double d2 = ( (Ant) bug2 ).getProbDieCenter();
			if ( d1 > d2 )    // d1 bigger, so it should sort after d2
				return 1;
			else if ( d1 < d2 )
				return -1;
			return 0;
		}
	}

	//////////////////////////////////////////////////////////////////////////////////
	// printProjectHelp
	// this should be filled in with some help to get from running as
	//        bin/guirun.sh -h
	//
	
	public void printProjectHelp() {
		System.out.printf( "\n%s -- AntPheromones 3 \n", getName() );

		System.out.printf( "\nAnts (aka bugs) move in a 2D toroidal grid in which\n" );
		System.out.printf( "'pheromone' is injected in the center, diffusing and evaporating.\n" );
		System.out.printf( "The ants move toward higher concentration of pheromone\n" );
		System.out.printf( "if they can, or they move to a randomly selected neighbor cell.\n" );
		System.out.printf( "\n" );

		System.out.printf( "\n" );
		System.out.printf( "Bugs have a probDieCenter if they are in the center cell.\n" );
		System.out.printf( "That falls off linearly to 0 at maxDistance from center.\n" );
		System.out.printf( "New Bugs are born to keep the antList size constant.\n" );
		System.out.printf( "\n" );

		System.out.printf( "Each step more pheromone is injected into the center cell,\n" );
		System.out.printf( "and it diffuses out and evaporates as controlled by the\n" );
		System.out.printf( "diffusionK and evapRate parameters.\n" );
		System.out.printf( "\n" );
		System.out.printf( "Each ant first checks is own probRandomMove to see if it\n" );
		System.out.printf( "moves randomly, and does not look at pheromones at all.\n" );
		System.out.printf( "If it moves randomly, it picks an open neighbor cell; \n" );
		System.out.printf( "  randomMoveMethod=0 -- pick randomly from open neighbors\n" );
		System.out.printf( "  randomMoveMethod=1 -- pick first found by getMooreNeighbors()\n" );

		System.out.printf( "\n" );
		System.out.printf( "If it doesn't move randomly, its picks open neighbor cell with\n" );
		System.out.printf( "most pheromone and moves there if more than current cell.\n" );

		System.out.printf( "\n" );
		System.out.printf( "If it has not moved randomly or to higher pheromone, it picks a random dx,dy\n" );
		System.out.printf( "within +1/-1 of its current location and tries to move there.\n" );
		System.out.printf( "It tries to make the move, but may fail because\n" );
		System.out.printf( "tried to move to a cell with an object in it\n" );
		System.out.printf( "If it can't move, it does nothing that step.\n" );
		System.out.printf( "\n" );

		System.out.printf( "Settable Parameterts:\n" );
		System.out.printf( "  SizeX, SizeY -- world size\n" );
		System.out.printf( "  numants -- number of ants to create.\n" );
		System.out.printf( "  diffusionK - 0 means none, 1 means max.\n" );
		System.out.printf( "  evapRate - 1 means none (!), 0 means max. (takes just 0.95...)\n" );
		System.out.printf( "  exogRate - rate of injection of exogenous pheromone. 1 = max.\n" );

		System.out.printf( "\n" );
		System.out.printf( "  activationOrder   0=fixed; 1=RWR, 2=RWOR \n" );
		System.out.printf( "  probRandomMoveMean -- init probability a bug moves randomly drawn from\n" );
		System.out.printf( "  probRandomMoveSD        this distribution\n" );
		System.out.printf( "  randomMoveMethod  - 0=unbiased choice of open neighbors; 1=pick first\n" );

		System.out.printf( "\n" );
		System.out.printf( "  probDieCenterMean -- init. probability a bug dies if in center drawn\n" );
		System.out.printf( "  probDieCenterSD        this distribution\n" );

		System.out.printf( "\n" );
		System.out.printf( "  probDieCenterMutSD\n" );
		System.out.printf( "  probRandomMoveSD\n" );
		System.out.printf( "\n" );
		
		System.out.printf( "\n" );
		System.out.printf( "  bestWinsProb  -- for tournament selection of parent\n" );
		System.out.printf( "  tournamentSize -- #candidates in tournament\n" );
		
		System.out.printf( "\n" );
		System.out.printf( "  initialSteps   - inject Pher, diffuse before adding ants\n" );
		System.out.printf( "\n" );
		System.out.printf( "  pherReportFileName - if not null, writes pheromone values\n" );
		System.out.printf( "  pherReportFrequency - how often it writes the values\n" );
		System.out.printf( "\n" );
		
		
		System.out.printf( "\n" );
		System.out.printf( "To run without eclipse:\n" );
		System.out.printf( "   ./guirun.sh\n" );
		System.out.printf( "   ./guirun.sh X=50 Y=50 nT=400\n" );
		System.out.printf( "   ./batchrun.sh T=500 X=50 Y=50 nT=400\n" );
		System.out.printf( "\n" );

		printParametersMap();

		System.out.printf( "\n" );

		printForGSDrone();
		
		System.exit( 0 );

	}



	///////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////
	//
	//            USUALLY NO NEED TO CHANGE THINGS BELOW HERE
	//
	///////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////



	////////////////////////////////////////////////////////////////////////////
	// constructor, if need to do anything special.
	public Model () {
	}

	///////////////////////////////////////////////////////////////////////////
	// setup
	// set generic defaults after a run start or restart
	// calls userSetup() which the model-author defines
	//   for the specific model.

	public void setup () {
		schedule = null;
		if ( rDebug > 1 )
			System.out.printf( "==> Model-setup...\n" );

		userSetup();

		System.gc ();   // garabage collection of discarded objects
		super.setup();  // THIS SHOULD BE CALLED after setting defaults in setup().
		schedule = new Schedule (1);  // create AFTER calling super.setup()

		if ( rDebug > 1 )
			System.out.printf( "\n<=== Model-setup() done.\n" );

	}

	///////////////////////////////////////////////////////////////////////////
	// buildModel
	// build the generic "architecture" for the model,
	// and call userBuildModel() which the model-author defines
	// to create the model-specific components.

	public void buildModel () {
		if ( rDebug > 1 )
			System.out.printf( "==> buildModel...\n" );

		// CALL FIRST -- defined in super class -- it starts RNG, etc
		buildModelStart();

		userBuildModel();

		// some post-load finishing touches
		startReportFile();

		// you probably don't want to remove any of the following
		// calls to process parameter changes and write the
		// initial state to the report file.
		// NB -> you might remove/add more agentChange processing
        applyAnyStoredChanges();
        stepReport();
        getReportFile().flush();
        getPlaintextReportFile().flush();

		if ( rDebug > 1 )
			System.out.printf( "<== buildModel done.\n" );
	}




	//////////////////////////////////////////////////////////////////////////////
	public Schedule getSchedule () {	return schedule; }

	public String getName () { return "Model"; }



	/////////////////////////////////////////////////////////////////////////////
	// processEndOfRun
	// called once, at end of run.
	// writes some final info, closes report files, etc.
	public void processEndOfRun ( ) {
		if ( rDebug > 0 )  
			System.out.printf("\n\n===== processEndOfRun =====\n\n" );
		applyAnyStoredChanges();
		endReportFile();

		if ( pherReportFile != null ) {
			endReportFile( pherReportFile );
			endPlainTextReportFile( pherPlainTextReportFile );
		}
		
		this.fireStopSim();
	}

}
