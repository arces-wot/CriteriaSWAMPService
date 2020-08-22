package it.unibo.arces.wot.sepa.tools.criteria;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
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
 * [software] software=CRITERIA1D
 * 
 * [project] name=SWAMP db_parameters=./data/modelParameters.db
 * db_soil=./data/soil_ER_2002.db db_meteo=./data/weather_observed.db
 * 
 * db_units=./data/units.db
 * 
 * db_output=./output/swamp.db
 * 
 * [forecast] isSeasonalForecast=false isShortTermForecast=false
 * 
 * 2) The 'db_units' includes all the cases to be evaluated.
 * 
 * Each project "unit" record is composed by:
 * 
 * - ID_CASE : an ID of the use case - ID_CROP : the ID of the crop to be
 * evaluated (the ID is a foreign key for the 'db_parameters') - ID_SOIL : the
 * ID of the soil to be evaluated (the ID is a foreign key for the 'db_soil') -
 * ID_METEO : the ID of the weather table (T_MAX, T_MIN, T_AVG, PREC, ETP,
 * WATER_TABLE) within the 'db_meteo'
 * 
 */
public class CriteriaSWAMPService {
	static String host = null;
	static String commandLine = "./CRITERIA1D";
	static int daysOfForecast = 3;

	static String db_meteo = "/data/weather.db";
	static String db_soil = "/data/soil.db";
	static String db_crop = "/data/crop.db";
	static String db_units = "/data/units.db";
	static String db_output = "/data/output.db";
	static String db_forecast = "/data/forecast.db";

	static enum MODE {
		COPY, SET, RUN, CRON
	};

	public static void main(String[] args)
			throws SEPAProtocolException, SEPASecurityException, SQLException, SEPAPropertiesException, IOException,
			SEPABindingsException, InterruptedException, URISyntaxException, ParseException {

		Date now = new Date();

		Calendar from = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
		Calendar to = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
		from.setTime(now);
		to.setTime(now);

		Map<String, String> env = System.getenv();
		MODE mode = MODE.CRON;
		int cronHH = 7;
		int cronMM = 30;

		for (String var : env.keySet()) {
			switch (var.toUpperCase()) {
			case "SEPA_HOST":
				host = env.get("SEPA_HOST");
				break;
			case "CMD":
				commandLine = env.get("CMD");
				break;

			case "DB_METEO":
				db_meteo = env.get("DB_METEO");
				break;
			case "DB_SOIL":
				db_soil = env.get("DB_SOIL");
				break;
			case "DB_CROP":
				db_crop = env.get("DB_CROP");
				break;
			case "DB_UNITS":
				db_units = env.get("DB_UNITS");
				break;
			case "DB_OUTPUT":
				db_output = env.get("DB_OUTPUT");
				break;
			case "DB_FORECAST":
				db_forecast = env.get("DB_FORECAST");
				break;
			case "DAYS_OF_FORECAST":
				daysOfForecast = Integer.parseInt(env.get("DAYS_OF_FORECAST"));
				break;

			case "SET_DATE":
				Date set = new SimpleDateFormat("yyyy-MM-dd").parse(env.get("SET_DATE"));
				from.setTime(set);
				to.setTime(set);
				break;
			case "FROM":
				set = new SimpleDateFormat("yyyy-MM-dd").parse(env.get("FROM"));
				from.setTime(set);
				break;
			case "TO":
				set = new SimpleDateFormat("yyyy-MM-dd").parse(env.get("TO"));
				to.setTime(set);
				break;

			case "MODE":
				String temp = env.get("MODE");
				switch (temp) {
				case "COPY":
					mode = MODE.COPY;
					break;
				case "SET":
					mode = MODE.SET;
					break;
				case "RUN":
					mode = MODE.RUN;
					break;
				default:
					// CRON-HH:MM
					if (temp.startsWith("CRON")) {
						cronHH = Integer.parseInt(temp.split("-")[1].split(":")[0]);
						cronMM = Integer.parseInt(temp.split("-")[1].split(":")[1]);
					}
				}
				break;
			default:
				break;
			}
		}

		Criteria criteria = new Criteria(commandLine, host, db_meteo, db_output, db_soil, db_crop, db_units,
				daysOfForecast, db_forecast);

		if (mode.equals(MODE.CRON)) {
			Calendar gmt = new GregorianCalendar(TimeZone.getTimeZone("GMT"));

			while (true) {
				now = new Date();
				gmt.setTime(now);
				
				if (gmt.get(Calendar.HOUR_OF_DAY) == cronHH && gmt.get(Calendar.MINUTE) == cronMM) {
					criteria.run(gmt);
					Thread.sleep(90000);
				}
				
				Thread.sleep(30000);				
			}
		} else if (mode.equals(MODE.COPY)) {
			long days = Duration.between(from.toInstant(), to.toInstant()).toDays();
			criteria.copyOutput(from, (int) days + 1);
		} else {
			Calendar sim = from;
			while (sim.before(to) || sim.equals(to)) {
				if (mode.equals(MODE.RUN))
					criteria.run(sim);
				else
					criteria.setWeatherDB(sim);
				sim.add(Calendar.DAY_OF_MONTH, 1);
			}
		}

	}
}
