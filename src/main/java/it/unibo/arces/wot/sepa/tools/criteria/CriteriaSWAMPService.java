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
	static String host = "mml.arces.unibo.it";
	static String commandLine = "./CRITERIA1D";
	static int forecastDays = 3;
	static String inputDB = "data/weather.db";
	static String outputDB = "data/swamp.db";

	public static void main(String[] args)
			throws SEPAProtocolException, SEPASecurityException, SQLException, SEPAPropertiesException, IOException,
			SEPABindingsException, InterruptedException, URISyntaxException, ParseException {

		Date now = new Date();

		Calendar from = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
		Calendar to = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
		from.setTime(now);
		to.setTime(now);

		Map<String, String> env = System.getenv();

		boolean copy = false;
		boolean setWeatherOnly = false;

		for (String var : env.keySet()) {
			switch (var.toUpperCase()) {
			case "SEPA_HOST":
				host = env.get("SEPA_HOST");
				break;
			case "CMD":
				commandLine = env.get("CMD");
				break;
			case "INPUT_DB":
				inputDB = env.get("INPUT_DB");
				break;
			case "OUTPUT_DB":
				outputDB = env.get("OUTPUT_DB");
				break;
			case "FORECAST_DAYS":
				forecastDays = Integer.parseInt(env.get("FORECAST_DAYS"));
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
			case "COPY":
				copy = true;
				break;
			case "SET_WEATHER_ONLY":
				setWeatherOnly = true;
				break;
			default:
				break;
			}
		}

		Criteria criteria = new Criteria(commandLine, host, inputDB, outputDB, forecastDays);

		if (copy) {
			long days = Duration.between(from.toInstant(), to.toInstant()).toDays();
			criteria.copyOutput(from, (int) days + 1);
		} else {
			Calendar sim = from;
			while (sim.before(to) || sim.equals(to)) {
				if (!setWeatherOnly) criteria.run(sim);
				else criteria.setWeatherDB(sim);
				sim.add(Calendar.DAY_OF_MONTH, 1);
			}
		}

	}
}
