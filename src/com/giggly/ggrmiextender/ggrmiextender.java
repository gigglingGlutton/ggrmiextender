package com.giggly.ggrmiextender;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.callbacks.CallbackApi;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import javassist.CannotCompileException;



public class ggrmiextender implements WurmServerMod, Configurable, Initable, PreInitable {
	
	public boolean merchantEnlist = false;
	public String merchantFormat = null;
	
	@Override
	public void configure(Properties prop) {
		this.merchantEnlist = Boolean.valueOf(prop.getProperty("merchantEnlist", Boolean.toString(merchantEnlist)));
		this.merchantFormat = prop.getProperty("merchantFormat", merchantFormat);
	}
	
	// main function
	@CallbackApi
	public String getInvFormatted(String tradersList) {
		if (tradersList != null && !tradersList.isEmpty() ) {
			ggmerchantoutput fmt = new ggmerchantoutput();
			String ret = "";
			switch (merchantFormat) {
				case "csv" : ret=fmt.csvOutput(tradersList);
				break;
				case "json" : ret=fmt.jsonOutput(tradersList);
				break;
				default : ret=fmt.htmlOutput(tradersList);
				break;
			}
			return ret;
			
		}
		return "Couldn't get traders inventory";
	}
	// Init callback
	@Override
	public void preInit() {
		try {
			CtClass rmiImplClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.webinterface.WebInterfaceImpl");
			HookManager.getInstance().addCallback(rmiImplClass, "ggrmiextender", this);
		}
		catch (NotFoundException e ) {
			throw new HookException(e);
		}
	}
	// Inject custom methods into wurm code
	@Override
	public void init() {
		if (merchantEnlist) {
				try {
					// Traders list query
					CtClass economyClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.economy.DbEconomy");
					CtMethod getTraders = CtNewMethod.make(							
						"public static String ggGetTradersList() {" + //
						"java.sql.Connection dbcon = null;" +
						"java.sql.PreparedStatement ps = null;" +
						"java.sql.ResultSet rs = null;" +
						"String tradersList = \"\";" +
						"try {" +
							"dbcon = com.wurmonline.server.DbConnector.getEconomyDbCon();" +
							"ps = dbcon.prepareStatement(\"SELECT WURMID FROM TRADER WHERE WURMID>0\");" +
							"rs = ps.executeQuery();" +
							"while (rs.next()) {" +
								"tradersList = tradersList + rs.getString(1) + \";\";" +
							"}" +
							"return tradersList;" +
						"}" +
						"catch (java.sql.SQLException sqx) {" +
							"return null;" +
						"}" +
					"}", economyClass);
					economyClass.addMethod(getTraders);
					// RMI abstract method
					CtClass rmiClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.webinterface.WebInterface");
					CtMethod ggRMIGetter = CtNewMethod.make("public abstract String ggGetTraderStore(String paramString) throws java.rmi.RemoteException;", rmiClass);
					rmiClass.addMethod(ggRMIGetter);
					// RMI method
					CtClass rmiImplClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.webinterface.WebInterfaceImpl");
					CtMethod ggRMIImplGetter = CtNewMethod.make("public String ggGetTraderStore(String paramString) throws java.rmi.RemoteException { " +
						"validateIntraServerPassword(paramString);" +
						"String tradersList = com.wurmonline.server.economy.DbEconomy.ggGetTradersList();" +
						"return ggrmiextender.getInvFormatted(tradersList);" +
						"}", rmiImplClass);
					rmiImplClass.addMethod(ggRMIImplGetter);
					
					//all good
					Logger.getLogger(ggrmiextender.class.getName()).log(Level.INFO, "Methods inserts succeded", "success");
				} catch (NotFoundException | CannotCompileException ex) {
					Logger.getLogger(ggrmiextender.class.getName()).log(Level.SEVERE, null, ex);
					throw new HookException(ex);
				}
				
			
		}
	}
	//Convert irons to gsci

	 
}

