package net.sf.openrocket.rocketcomponent;

import java.util.Collection;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.openrocket.l10n.Translator;
import net.sf.openrocket.startup.Application;
import net.sf.openrocket.util.ArrayList;
import net.sf.openrocket.util.Coordinate;
import net.sf.openrocket.util.MathUtil;
import net.sf.openrocket.util.StateChangeListener;
import net.sf.openrocket.util.UniqueID;


/**
 * Base for all rocket components.  This is the "starting point" for all rocket trees.
 * It provides the actual implementations of several methods defined in RocketComponent
 * (eg. the rocket listener lists) and the methods defined in RocketComponent call these.
 * It also defines some other methods that concern the whole rocket, and helper methods
 * that keep information about the program state.
 *
 * @author Sampo Niskanen <sampo.niskanen@iki.fi>
 */

public class Rocket extends RocketComponent {
	private static final Logger log = LoggerFactory.getLogger(Rocket.class);
	private static final Translator trans = Application.getTranslator();
	
	public static final String DEFAULT_NAME = "[{motors}]";
	public static final double DEFAULT_REFERENCE_LENGTH = 0.01;
	
	
	/**
	 * List of component change listeners.
	 */
	private List<EventListener> listenerList = new ArrayList<EventListener>();
	
	/**
	 * When freezeList != null, events are not dispatched but stored in the list.
	 * When the structure is thawed, a single combined event will be fired.
	 */
	private List<ComponentChangeEvent> freezeList = null;
	
	
	private int modID;
	private int massModID;
	private int aeroModID;
	private int treeModID;
	private int functionalModID;
	
	private boolean eventsEnabled=false;
	
	private ReferenceType refType = ReferenceType.MAXIMUM; // Set in constructor
	private double customReferenceLength = DEFAULT_REFERENCE_LENGTH;
	
	
	private String designer = "";
	private String revision = "";
	
	
	// Flight configuration list
	private FlightConfiguration selectedConfiguration;
	private HashMap<FlightConfigurationId, FlightConfiguration> configSet = new HashMap<FlightConfigurationId, FlightConfiguration>(); 
	private HashMap<Integer, AxialStage> stageMap = new HashMap<Integer, AxialStage>();
	
	// Does the rocket have a perfect finish (a notable amount of laminar flow)
	private boolean perfectFinish = false;
	
	
	/////////////  Constructor  /////////////
	
	public Rocket() {
		super(RocketComponent.Position.AFTER);
		modID = UniqueID.next();
		massModID = modID;
		aeroModID = modID;
		treeModID = modID;
		functionalModID = modID;
		

		
		// must be after the hashmaps :P 
		this.selectedConfiguration = new FlightConfiguration( this, null);
	}
	
	public String getDesigner() {
		checkState();
		return designer;
	}
	
	public void setDesigner(String s) {
		if (s == null)
			s = "";
		designer = s;
		fireComponentChangeEvent(ComponentChangeEvent.NONFUNCTIONAL_CHANGE);
	}
	
	
	public String getRevision() {
		checkState();
		return revision;
	}
	
	public void setRevision(String s) {
		if (s == null)
			s = "";
		revision = s;
		fireComponentChangeEvent(ComponentChangeEvent.NONFUNCTIONAL_CHANGE);
	}
	
	/**
	 * Return the number of stages in this rocket.
	 *
	 * @return   the number of stages in this rocket.
	 */
	public int getStageCount() {
		checkState();
		return this.stageMap.size();
	}
	
	/**
	 * Return the non-negative modification ID of this rocket.  The ID is changed
	 * every time any change occurs in the rocket.  This can be used to check
	 * whether it is necessary to void cached data in cases where listeners can not
	 * or should not be used.
	 * <p>
	 * Three other modification IDs are also available, {@link #getMassModID()},
	 * {@link #getAerodynamicModID()} {@link #getTreeModID()}, which change every time
	 * a mass change, aerodynamic change, or tree change occur.  Even though the values
	 * of the different modification ID's may be equal, they should be treated totally
	 * separate.
	 * <p>
	 * Note that undo events restore the modification IDs that were in use at the
	 * corresponding undo level.  Subsequent modifications, however, produce modIDs
	 * distinct from those already used.
	 *
	 * @return   a unique ID number for this modification state.
	 */
	public int getModID() {
		return modID;
	}
	
	/**
	 * Return the non-negative mass modification ID of this rocket.  See
	 * {@link #getModID()} for details.
	 *
	 * @return   a unique ID number for this mass-modification state.
	 */
	public int getMassModID() {
		return massModID;
	}
	
	/**
	 * Return the non-negative aerodynamic modification ID of this rocket.  See
	 * {@link #getModID()} for details.
	 *
	 * @return   a unique ID number for this aerodynamic-modification state.
	 */
	public int getAerodynamicModID() {
		return aeroModID;
	}
	
	/**
	 * Return the non-negative tree modification ID of this rocket.  See
	 * {@link #getModID()} for details.
	 *
	 * @return   a unique ID number for this tree-modification state.
	 */
	public int getTreeModID() {
		return treeModID;
	}
	
	/**
	 * Return the non-negative functional modificationID of this rocket.
	 * This changes every time a functional change occurs.
	 *
	 * @return	a unique ID number for this functional modification state.
	 */
	public int getFunctionalModID() {
		return functionalModID;
	}
	
	public Collection<AxialStage> getStageList() {
		return this.stageMap.values();
	}
	
	/*
	 * Returns the stage at the top of the central stack
	 * 
	 * @Return a reference to the topmost stage
	 */
	public AxialStage getTopmostStage(){
		return (AxialStage) getChild(0);
	}
	
	/*
	 * Returns the stage at the top of the central stack
	 * 
	 * @Return a reference to the topmost stage
	 */
	public AxialStage getBottomCoreStage(){
		// get last stage that's a direct child of the rocket.
		return (AxialStage) children.get( children.size()-1 );
	}
	
	private int getNewStageNumber() {
		int guess = 0;
		while (stageMap.containsKey(guess)) {
			guess++;
		}
		return guess;
	}
	
	public void trackStage(final AxialStage newStage) {
		int stageNumber = newStage.getStageNumber();
		AxialStage value = stageMap.get(stageNumber);
		
		if (newStage.equals(value)) {
			// stage is already added. skip.
		} else {
			stageNumber = getNewStageNumber();
			newStage.setStageNumber(stageNumber);
			this.stageMap.put(stageNumber, newStage);
		}
	}
	
	public void forgetStage(final AxialStage oldStage) {
		this.stageMap.remove(oldStage.getStageNumber());
	}
	
	public ReferenceType getReferenceType() {
		checkState();
		return refType;
	}
	
	public void setReferenceType(ReferenceType type) {
		if (refType == type)
			return;
		refType = type;
		fireComponentChangeEvent(ComponentChangeEvent.NONFUNCTIONAL_CHANGE);
	}
	
	
	public double getCustomReferenceLength() {
		checkState();
		return customReferenceLength;
	}
	
	public void setCustomReferenceLength(double length) {
		if (MathUtil.equals(customReferenceLength, length))
			return;
		
		this.customReferenceLength = Math.max(length, 0.001);
		
		if (refType == ReferenceType.CUSTOM) {
			fireComponentChangeEvent(ComponentChangeEvent.NONFUNCTIONAL_CHANGE);
		}
	}
	
	
	
	
	
	/**
	 * Set whether the rocket has a perfect finish.  This will affect whether the
	 * boundary layer is assumed to be fully turbulent or not.
	 *
	 * @param perfectFinish		whether the finish is perfect.
	 */
	public void setPerfectFinish(boolean perfectFinish) {
		if (this.perfectFinish == perfectFinish)
			return;
		this.perfectFinish = perfectFinish;
		fireComponentChangeEvent(ComponentChangeEvent.AERODYNAMIC_CHANGE);
	}
	
	
	
	/**
	 * Get whether the rocket has a perfect finish.
	 *
	 * @return the perfectFinish
	 */
	public boolean isPerfectFinish() {
		return perfectFinish;
	}
	
	
	
	/**
	 * Make a shallow copy of the Rocket structure.  This method is exposed as public to allow
	 * for undo/redo system functionality.
	 * 
	 * note:  the <hashmap>.clone() function returns a shallow copy-- which is probably appropriate. 
	 */
	@Override
	public Rocket copyWithOriginalID() {
		Rocket copy = (Rocket) super.copyWithOriginalID();
		
		// Rocket copy is cloned, so non-trivial members must be cloned as well:
		copy.stageMap = new HashMap<Integer, AxialStage>();
		copy.configSet = new HashMap<FlightConfigurationId, FlightConfiguration>();
		if( 0 < this.configSet.size() ){
			Rocket.cloneConfigs( this, copy);
		}
		copy.listenerList = new ArrayList<EventListener>();
		
		return copy;
	}
	
	private static void cloneConfigs( final Rocket source, Rocket dest ){
		source.checkState();
		dest.checkState();
		dest.selectedConfiguration = source.selectedConfiguration.clone();
		for( final FlightConfiguration config : source.configSet.values() ){
			dest.configSet.put( config.getId(), config.clone() );
		}
	}
	
	public int getFlightConfigurationCount() {
		checkState();
		return this.configSet.size();
	}
	
	/**
	 * Load the rocket structure from the source.  The method loads the fields of this
	 * Rocket object and copies the references to siblings from the <code>source</code>.
	 * The object <code>source</code> should not be used after this call, as it is in
	 * an illegal state!
	 * <p>
	 * This method is meant to be used in conjunction with undo/redo functionality,
	 * and therefore fires an UNDO_EVENT, masked with all applicable mass/aerodynamic/tree
	 * changes.
	 */
	public void loadFrom(Rocket r) {
		
		// Store list of components to invalidate after event has been fired
		List<RocketComponent> toInvalidate = this.copyFrom(r);
		
		int type = ComponentChangeEvent.UNDO_CHANGE | ComponentChangeEvent.NONFUNCTIONAL_CHANGE;
		if (this.massModID != r.massModID)
			type |= ComponentChangeEvent.MASS_CHANGE;
		if (this.aeroModID != r.aeroModID)
			type |= ComponentChangeEvent.AERODYNAMIC_CHANGE;
		// Loading a rocket is always a tree change since the component objects change
		type |= ComponentChangeEvent.TREE_CHANGE;
		
		this.modID = r.modID;
		this.massModID = r.massModID;
		this.aeroModID = r.aeroModID;
		this.treeModID = r.treeModID;
		this.functionalModID = r.functionalModID;
		this.refType = r.refType;
		this.customReferenceLength = r.customReferenceLength;
		Rocket.cloneConfigs( r, this);
		
		this.perfectFinish = r.perfectFinish;
		
		this.checkComponentStructure();
		
		fireComponentChangeEvent(type);
		
		// Invalidate obsolete components after event
		for (RocketComponent c : toInvalidate) {
			c.invalidate();
		}
	}
	
	
	
	
	///////  Implement the ComponentChangeListener lists
	
	/**
	 * Creates a new EventListenerList for this component.  This is necessary when cloning
	 * the structure.
	 */
	public void resetListeners() {
		//		System.out.println("RESETTING LISTENER LIST of Rocket "+this);
		listenerList = new ArrayList<EventListener>();
	}
	
	
	public void printListeners() {
		System.out.println("" + this + " has " + listenerList.size() + " listeners:");
		int i = 0;
		for (EventListener l : listenerList) {
			System.out.println("  " + (i) + ": " + l);
			i++;
		}
	}
	
	@Override
	public void addComponentChangeListener(ComponentChangeListener l) {
		checkState();
		listenerList.add(l);
		log.trace("Added ComponentChangeListener " + l + ", current number of listeners is " +
				listenerList.size());
	}
	
	@Override
	public void removeComponentChangeListener(ComponentChangeListener l) {
		listenerList.remove(l);
		log.trace("Removed ComponentChangeListener " + l + ", current number of listeners is " +
				listenerList.size());
	}
	
	@Override
	protected void fireComponentChangeEvent(ComponentChangeEvent cce) {
		if( ! this.eventsEnabled ){
			return;
		}
		
		mutex.lock("fireComponentChangeEvent");
		try {
			checkState();
			
			// Update modification ID's only for normal (not undo/redo) events
			if (!cce.isUndoChange()) {
				modID = UniqueID.next();
				if (cce.isMassChange())
					massModID = modID;
				if (cce.isAerodynamicChange())
					aeroModID = modID;
				if (cce.isTreeChange())
					treeModID = modID;
				if (cce.isFunctionalChange())
					functionalModID = modID;
			}
			
			// Update modification ID's only for normal (not undo/redo) events
			{ // vvvv DEVEL vvvv
//				String changeString; 
//				if (cce.isUndoChange()) {
//					changeString = "an 'undo' change from: "+cce.getSource().getName()+"  as:"+cce.toString();
//				}else{
//					changeString = "a normal change from: "+cce.getSource().getName()+"  as:"+cce.toString();
//				}
//				
//				log.error("Processing a rocket change: "+changeString, new IllegalArgumentException());
			} // ^^^^ DEVEL ^^^^
			
			// Check whether frozen
			if (freezeList != null) {
				log.debug("Rocket is in frozen state, adding event " + cce + " info freeze list");
				freezeList.add(cce);
				return;
			}
		
			// Notify all components first
			Iterator<RocketComponent> iterator = this.iterator(true);
			while (iterator.hasNext()) {
				iterator.next().componentChanged(cce);
			}
			
			// notify all configurations
			this.update();
			
			// Notify all listeners
			// Copy the list before iterating to prevent concurrent modification exceptions.
			EventListener[] list = listenerList.toArray(new EventListener[0]);
			for (EventListener l : list) {
				if (l instanceof ComponentChangeListener) {
					((ComponentChangeListener) l).componentChanged(cce);
				} else if (l instanceof StateChangeListener) {
					((StateChangeListener) l).stateChanged(cce);
				}
			}
		} finally {
			mutex.unlock("fireComponentChangeEvent");
		}
	}
	
	@Override
	public void update(){
		this.selectedConfiguration.update();
		for( FlightConfiguration config : configSet.values() ){
			config.update();
		}
	}
	
	/**
	 * Freezes the rocket structure from firing any events.  This may be performed to
	 * combine several actions on the structure into a single large action.
	 * <code>thaw()</code> must always be called afterwards.
	 *
	 * NOTE:  Always use a try/finally to ensure <code>thaw()</code> is called:
	 * <pre>
	 *     Rocket r = c.getRocket();
	 *     try {
	 *         r.freeze();
	 *         // do stuff
	 *     } finally {
	 *         r.thaw();
	 *     }
	 * </pre>
	 *
	 * @see #thaw()
	 */
	public void freeze() {
		checkState();
		if (freezeList == null) {
			freezeList = new LinkedList<ComponentChangeEvent>();
			log.debug("Freezing Rocket");
		} else {
			Application.getExceptionHandler().handleErrorCondition("Attempting to freeze Rocket when it is already frozen, " +
					"freezeList=" + freezeList);
		}
	}
	
	/**
	 * Thaws a frozen rocket structure and fires a combination of the events fired during
	 * the freeze.  The event type is a combination of those fired and the source is the
	 * last component to have been an event source.
	 *
	 * @see #freeze()
	 */
	public void thaw() {
		checkState();
		if (freezeList == null) {
			Application.getExceptionHandler().handleErrorCondition("Attempting to thaw Rocket when it is not frozen");
			return;
		}
		if (freezeList.size() == 0) {
			log.warn("Thawing rocket with no changes made");
			freezeList = null;
			return;
		}
		
		log.debug("Thawing rocket, freezeList=" + freezeList);
		
		int type = 0;
		Object c = null;
		for (ComponentChangeEvent e : freezeList) {
			type = type | e.getType();
			c = e.getSource();
		}
		freezeList = null;
		
		fireComponentChangeEvent(new ComponentChangeEvent((RocketComponent) c, type));
	}
	
	
	
	
	////////  Motor configurations  ////////
	
	
	/**
	 * Return the currently selected configuration.  This should be used in the user interface
	 * to ensure a consistent rocket configuration between dialogs.  It should NOT
	 * be used in simulations not relating to the UI.
	 *
	 * @return   the current {@link FlightConfiguration}.
	 */
	public FlightConfiguration getSelectedConfiguration() {
		checkState();
		return this.selectedConfiguration;
	}
	
	public FlightConfiguration createFlightConfiguration( final FlightConfigurationId fcid) {
		checkState();
		if( null == fcid ){
			throw new NullPointerException("Attempted to create a flightConfiguration from a null key!");
		}else if( fcid.hasError() ){
			throw new NullPointerException("Attempted to create a flightConfiguration from an error key!");
		}else if( configSet.containsKey(fcid)){
			return this.configSet.get(fcid);
		}else{
			FlightConfiguration nextConfig = new FlightConfiguration(this, fcid);
			this.configSet.put(fcid, nextConfig);
			this.selectedConfiguration = nextConfig;
			fireComponentChangeEvent(ComponentChangeEvent.TREE_CHANGE);
			return nextConfig;
		}
	}
	
	public int getConfigurationCount(){
		return this.configSet.size();
	}
	
	public List<FlightConfigurationId> getIds(){
		ArrayList<FlightConfigurationId> toReturn = new ArrayList<FlightConfigurationId>(this.configSet.keySet()); 
		
		// Java 1.8:
		//toReturn.sort( null );
			
		// Java 1.7: 
		Collections.sort(toReturn);
				
		return toReturn;
	}

	
	/**
	 * Primarily for use with UI elements 
	 * 
	 * @return list of attached flight configurations (unordered)
	 */
	public FlightConfiguration[] toConfigArray(){
		return this.configSet.values().toArray( new FlightConfiguration[0]);
	}
	
	/**
	 * Remove a flight configuration ID from the configuration IDs.  The <code>null</code>
	 * ID cannot be removed, and an attempt to remove it will be silently ignored.
	 *
	 * @param id   the flight configuration ID to remove
	 */
	public void removeFlightConfigurationID(FlightConfigurationId fcid) {
		checkState();
		if( fcid.hasError() ){
			return;
		}
		
		// Get current configuration:
		this.configSet.remove( fcid);
		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);
	}
	
	
	/**
	 * Check whether <code>id</code> is a valid motor configuration ID.
	 *
	 * @param id	the configuration ID.
	 * @return		whether a motor configuration with that ID exists.
	 */
	public boolean containsFlightConfigurationID(FlightConfigurationId id) {
		checkState();
		if( id.hasError() ){
			return false;
		}
		return configSet.containsKey( id);
	}
	
	
	/**
	 * Check whether the given motor configuration ID has motors defined for it.
	 *
	 * @param id	the FlightConfigurationID containing the motor (may be invalid).
	 * @return		whether any motors are defined for it.
	 */
	public boolean hasMotors(FlightConfigurationId fcid) {
		checkState();
		if( fcid.hasError() ){
			return false;
		}
		
		Iterator<RocketComponent> iterator = this.iterator();
		while (iterator.hasNext()) {
			RocketComponent c = iterator.next();
			
			if (c instanceof MotorMount) {
				MotorMount mount = (MotorMount) c;
				if (!mount.isMotorMount())
					continue;
				if (mount.getMotorInstance(fcid).getMotor() != null) {
					return true;
				}
			}
		}
		return false;
	}
	
	
	/**
	 * Return a flight configuration.  If the supplied id does not have a specific instance, the default is returned.  
	 *
	 * @param id   the flight configuration id
	 * @return	   a FlightConfiguration instance 
	 */
	public FlightConfiguration getFlightConfiguration(final FlightConfigurationId fcid) {
		checkState();
		return this.createFlightConfiguration(fcid);
	}

	/**
	 * Return a flight configuration.  If the supplied index is out of bounds, an exception is thrown.  
	 *
	 * @param id   the flight configuration index number
	 * @return	   a FlightConfiguration instance 
	 */
	public FlightConfiguration getFlightConfiguration(final int configIndex) {
		return this.configSet.get( this.getId(configIndex));
	}

	public FlightConfigurationId getId( final int configIndex) {
		List<FlightConfigurationId> idList = this.getIds();
		return idList.get(configIndex);
	}

	public void setSelectedConfiguration(final FlightConfiguration config) {
		checkState();
		this.selectedConfiguration = config;
		fireComponentChangeEvent(ComponentChangeEvent.NONFUNCTIONAL_CHANGE);
	}	
	
	public void setDefaultConfiguration(final FlightConfigurationId fcid) {
		checkState();
		
		if( fcid.hasError() ){
			log.error("attempt to set a 'fcid = config' with a error fcid.  Ignored.", new IllegalArgumentException("error id:"+fcid));
			return;
		}else if( this.configSet.containsKey(fcid)){
			this.selectedConfiguration = configSet.get(fcid);
			fireComponentChangeEvent(ComponentChangeEvent.NONFUNCTIONAL_CHANGE);
		}
	}	
	
	/**
	 * Associate the given ID and flight configuration.
	 * <code>null</code> or an empty string.
	 *
	 * @param id	the flight configuration id
	 * @param name	the name for the flight configuration
	 */
	public void setFlightConfiguration(final FlightConfigurationId fcid, FlightConfiguration newConfig) {
		checkState();
		if( fcid.hasError() ){
			log.error("attempt to set a 'fcid = config' with a error fcid.  Ignored.", new IllegalArgumentException("error id:"+fcid));
			return;
		}

		if (null == newConfig){
			newConfig = createFlightConfiguration(fcid);
		}
		configSet.put(fcid, newConfig);
		fireComponentChangeEvent(ComponentChangeEvent.NONFUNCTIONAL_CHANGE);
	}
	
	
	////////  Obligatory component information
	@Override
	public String getComponentName() {
		//// Rocket
		return trans.get("Rocket.compname.Rocket");
	}
	
	@Override
	public Coordinate getComponentCG() {
		return new Coordinate(0, 0, 0, 0);
	}
	
	@Override
	public double getComponentMass() {
		return 0;
	}
	
	@Override
	public double getLongitudinalUnitInertia() {
		return 0;
	}
	
	@Override
	public double getRotationalUnitInertia() {
		return 0;
	}
	
	@Override
	public Collection<Coordinate> getComponentBounds() {
		return Collections.emptyList();
	}
	
	@Override
	public boolean isAerodynamic() {
		return false;
	}
	
	@Override
	public boolean isMassive() {
		return false;
	}
	
	@Override
	public boolean allowsChildren() {
		return true;
	}
	
	/**
	 * Allows only <code>AxialStage</code> components to be added to the type Rocket.
	 */
	@Override
	public boolean isCompatible(Class<? extends RocketComponent> type) {
		return (AxialStage.class.equals(type));
	}

	/** 
	 * STUB.  would enable the monitoring, relay and production of events in this rocket instance.
	 */
	public void enableEvents() {
		this.enableEvents(true);
	}
	
	/** 
	 * STUB.  would enable the monitoring, relay and production of events in this rocket instance.
	 */
	public void enableEvents( final boolean _enable ) {
		if( this.eventsEnabled && _enable){
			return;
		}else if( _enable ){
			this.eventsEnabled = true;
			this.fireComponentChangeEvent(ComponentChangeEvent.AEROMASS_CHANGE);
		}else{
			this.eventsEnabled = false;
		}
	}
	
	public String toDebugConfigs(){
		StringBuilder buf = new StringBuilder();
		buf.append(String.format("====== Dumping %d Configurations from rocket: \n", this.getConfigurationCount(), this.getName()));
		final String fmt = "    [%-12s]: %s\n";
		buf.append(String.format(fmt, "  *SELECTED* ", selectedConfiguration.getName() ));
		for( FlightConfiguration config : this.configSet.values() ){
			String shortKey = config.getId().toShortKey();
			if( this.selectedConfiguration.equals( config)){
				shortKey = "*"+shortKey+"*";
			}
			buf.append(String.format(fmt, shortKey, config.getName() ));
		}
		return buf.toString();
	}
	
}
