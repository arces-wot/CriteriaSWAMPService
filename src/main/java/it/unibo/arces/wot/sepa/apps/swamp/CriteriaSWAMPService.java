package it.unibo.arces.wot.sepa.apps.swamp;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.GregorianCalendar;
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
	static String path = "/Users/luca/Documents/workspace/CRITERIA3D-master/build-swamp-Desktop_Qt_5_8_0_clang_64bit-Debug";
	static String weatherPath = path + "/swamp/weather.db";
	static String irrigationPath = path + "/swamp/irrigation.db";
	static String commandLine = "open " + path + "/swampService";
	static int forcastDays = 3;

	public static void main(String[] args) throws SEPAProtocolException, SEPASecurityException, SQLException,
			SEPAPropertiesException, IOException, SEPABindingsException, InterruptedException {
		Criteria criteria = new Criteria(weatherPath, irrigationPath, commandLine);

		Calendar today = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
		today.set(2019, 6, 23);
		criteria.run(today, forcastDays);
		criteria.close();
		
//		Calendar end = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
//		end.set(2019, 6, 23); // 2019-07-23
//		
//		while (today.before(end)) {
//			criteria.run(today, forcastDays);
//			today.add(Calendar.DAY_OF_MONTH, 1);
//		}

//		criteria.close();
	}
}
