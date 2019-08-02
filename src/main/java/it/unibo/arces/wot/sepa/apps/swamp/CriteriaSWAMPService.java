package it.unibo.arces.wot.sepa.apps.swamp;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.TimeZone;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;

/**
 * To setup CRITERIA consider the followings:
 * 
 * 1) The "swamp.ini" file contains the references to the DB files
 * 
 * [project] 
 * name="SWAMP" 
 * DBparameters="./swamp/crop.db"
 * DBsoil="./swamp/soil.db" 
 * DBmeteo="./swamp/weather.db"
 * DBunits="./swamp/units.db" 
 * DBoutput="./swamp/irrigation.db"
 * 
 * [forecast] 
 * isSeasonalForecast=false 
 * isShortTermForecast=false
 * 
 * 2) The "units.db" includes all the cases to be evaluated. Each project "unit"
 * record is composed by:
 * 
 * - ID_CASE : an ID of the use case 
 * - ID_CROP : the ID of the crop to be evaluated (the ID is a foreign key for the "crop.db") 
 * - ID_SOIL : the ID of the soil to be evaluated (the ID is a foreign key for the "crop.db") 
 * - ID_METERO : the ID of the weather table (T_MAX, T_MIN, T_AVG, PREC, ETP, WATER_TABLE) within the "weather.db"
 * 
 */
public class CriteriaSWAMPService {
	static String host = "host.docker.internal,";
	static String commandLine = "./CRITERIA1D";
	static int forecastDays = 3;
	static String weatherDB = "weather.db";
	static String irrigationDB = "irrigation.db";
	
	public static void main(String[] args) throws SEPAProtocolException, SEPASecurityException, SQLException,
			SEPAPropertiesException, IOException, SEPABindingsException, InterruptedException, URISyntaxException, ParseException {
		
		Date now = new Date();
		Calendar today = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
		today.setTime(now);
		
		Map<String, String> env = System.getenv();
		
		for(String var : env.keySet()) {
			switch (var.toUpperCase()) {
			case "SEPA_HOST":
				host = env.get("SEPA_HOST");
				break;
			case "CMD":
				commandLine = env.get("CMD");
				break;
			case "WEATHER_DB":
				weatherDB = env.get("WEATHER_DB");
				break;
			case "IRRIGATION_DB":
				irrigationDB = env.get("IRRIGATION_DB");
				break;
			case "FORECAST_DAYS":
				forecastDays = Integer.parseInt(env.get("FORECAST_DAYS"));
				break;
			case "SET_DATE":
				Date set=new SimpleDateFormat("yyyy-MM-dd").parse(env.get("SET_DATE"));
				today.setTime(set);
				break;
			default:
				break;
			}
		}
		
		Criteria criteria = new Criteria(commandLine,host,weatherDB,irrigationDB,forecastDays);
		criteria.run(today);
	}
}
