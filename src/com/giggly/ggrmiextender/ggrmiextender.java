package com.giggly.ggrmiextender;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.Creatures;
import com.wurmonline.server.creatures.NoSuchCreatureException;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemSpellEffects;
import com.wurmonline.server.spells.SpellEffect;
import static com.wurmonline.shared.util.MaterialUtilities.getRarityString;
import static com.wurmonline.server.items.Materials.convertMaterialByteIntoString;

import java.util.ArrayList;
import java.util.List;
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
	
	@Override
	public void configure(Properties prop) {
		this.merchantEnlist = Boolean.valueOf(prop.getProperty("merchantEnlist", Boolean.toString(merchantEnlist)));
	}
	
	// main function
	@CallbackApi
	public String getInvFormatted(String tradersList) {
		if (tradersList != null && !tradersList.isEmpty() ) {
			long traderID = 0;
			String ret = "<table><tr><th></th><th></th><th></th></tr>";
			int itemCount = 0;
			String items = "";
			String spells;
			String price;
			String[] splitTraders = tradersList.split(";");
			for (String str : splitTraders) {
				traderID = Long.parseLong(str, 10);
				try {
					Creature trader = Creatures.getInstance().getCreature(traderID);
					itemCount = trader.getNumberOfShopItems();
					if (itemCount>0) {
						@SuppressWarnings("unchecked")
						List<Item> inventory = new ArrayList(trader.getInventory().getItems());
						items = "";
						for (Item item : inventory) {
							spells = "";
							//copy from wurm code
							ItemSpellEffects eff = item.getSpellEffects();
							if (eff != null) {
								SpellEffect[] speffs = eff.getEffects();
								for (int x = 0; x < speffs.length; x++) {
									if (speffs[x].type < -10L) {
										spells = spells + speffs[x].getName() + ";";
									} else {
										spells = spells + speffs[x].getName() + "[" + (int)speffs[x].power + "];";
									}
								}
							}
							// Price if set or default value if not
							price = (item.getPrice() == 0)? formatPrice(item.getValue()) : formatPrice(item.getPrice());
							items = items + "<tr><td>" + getRarityString(item.getRarity()) + " " + item.getName() + "," + convertMaterialByteIntoString(item.getMaterial()) + "</td><td>" + spells + "</td><td>" + item.getQualityLevel() + "</td><td>" + price + "</td></tr>";
						}
						ret = ret + "<tr><td>" + trader.getName() + "</td><td>" + trader.getPosX() + ":" + trader.getPosY() +"</td><td><table>" + items + "</table></td></tr>";
					}
				} 
				catch (NoSuchCreatureException e) {
					Logger.getLogger(ggrmiextender.class.getName()).log(Level.WARNING, "No such creature exception at ggrmiextender ", "No such creature exception at ggrmiextender ");
				}
				
			}
			return ret + "</table>";
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
	 public String formatPrice(int price)
  {
    int a = price;
	String b = "";
	String c = "";
	String[] i = {"i","c","s","g"};
	int t;
	int j = 0;
		
	while (a>0) {
		t=a%100;
		c=b;
		if (t!=0){ b = t + i[j] + c;}
		a=(a-t)/100;
		j++;
	}
    return b;
  }
	 
}

