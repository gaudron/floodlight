package net.floodlightcontroller.context;

public class Condition {

	
	protected boolean isActive;
	protected String type;
	public String value;

	public Condition(String type, String value){
		this.isActive=false;
		this.type=type;
		this.value=value;
	}
	
	public void activeCondition() {
		this.isActive=true;
		System.out.println("Condition of type: " + type + " and value: " + value + " has been activated");
	}
	
	public String getType() {
		return this.type;
	}
	
	public String getValue() {
		return this.value;
	}
	
	public boolean isActive(){
		return this.isActive;
	}
}