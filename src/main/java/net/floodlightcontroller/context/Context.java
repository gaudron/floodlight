package net.floodlightcontroller.context;

import java.util.ArrayList;

public class Context {

	protected boolean isActive;
	protected ArrayList<Condition> conditions;
	protected String contextName;
	protected int tenSecondsUnits; 
	protected int compteur;
	
	public Context(String name){
		this.contextName = name;
		this.isActive=false;
		this.conditions = new ArrayList<Condition>();
		this.compteur = 0;
		this.tenSecondsUnits = 6;
	}
	
	public boolean isActive(){
		for (Condition condition : conditions) {
			if(!condition.isActive) return false;
		}
		return true;
	}
	
	public void active(){
		if(isActive()) this.isActive= true;
	}
	
	public ArrayList<Condition> getConditions() {
		return this.conditions;
	}
	//if(cond.getType().equals(type) && cond.getValue().equals(value)
	public boolean activeCondition (String type, String value) {
		for (Condition condition : conditions) {
			if(condition.getType().contains(type) && value.contains(condition.getValue())) {
				if (!condition.isActive) condition.activeCondition();
				return true;
			}
		}
		return false;
	}
	
	public void addCondition(String param, String value) {
		Condition cond = new Condition(param,value);
		conditions.add(cond);
	}
	
	public String getContextName() {
		return this.contextName;
	}
	
	public void increase () {
		if(compteur < tenSecondsUnits) this.compteur ++;
	}
	
	public boolean isTimeUp() {
		if (tenSecondsUnits == compteur) return true;
		else return false;
	}
	
	
	
}