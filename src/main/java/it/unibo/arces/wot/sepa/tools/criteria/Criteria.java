package it.unibo.arces.wot.sepa.tools.criteria;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.response.QueryResponse;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.commons.sparql.Bindings;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermLiteral;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermURI;
import it.unibo.arces.wot.sepa.pattern.GenericClient;
import it.unibo.arces.wot.sepa.pattern.JSAP;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.Map.Entry;
import java.util.Scanner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ini4j.InvalidFileFormatException;
import org.ini4j.Wini;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/*
 * From Fausto Tomei, 07-08-2019
 * 
 
 - meteo: in weather_observed trovate il meteo, dal 2001 ad oggi, della cella ERG5 su cui ricadono i campi (id 01015), con e senza falda stimata 
 (quello con falda termina con 'WT' , che sta per watertable).

Non è necessario che usiate tutti questi dati per la versione operativa, noi l'abbiamo fatto per controllare le irrigazioni stimate dal 2001 ad oggi per pero e vite, 
che trovate nel foglio excel in /output/

Per la versione operativa è sufficiente che inseriate i dati dal primo gennaio dell'anno precedente rispetto al periodo di previsione (quindi dal 2018-01-01).

Conviene anche a voi creare due meteo diversi, uno con e uno senza falda, per gestire i vari casi (vedi sotto).

- falda: la profondità di falda va inserita in metri. 

Controllate che i dati del piezometro siano coerenti con la stima (ora dovrebbe essere intorno a 2.5 metri) se non fosse così 
(cioè se la differenza è alta, diciamo oltre il mezzo metro) girateci i dati del piezometro che cerchiamo un pozzo più simile. 
Per i giorni previsti potete replicare l'ultimo dato osservato. Quando il piezometro è secco inserite un valore inferiore al valore minimo 
(se il piezometro arriva a 3 metri, inserite ad esempio 3.3)

- soil: mantenete per compatibilità la versione del db che state usando, come vedete in units il suolo che abbiamo utilizzato per le simulazioni è SMB1 (id 267).

- crop: in modelParameters.db abbiamo inserito due nuove colture che Giulia aveva tarato per simulare vite e pera in swamp, sono PEARABATE (codice WPpa) e GRAPEVINELAMBRUSCO (WPvl). 
Potete copiare direttamente modelParameters.db per usarle oppure copiare le due righe corrispondenti nelle tabelle crop e crop_class. 
C'è però un possibile problema di compatibilità da controllare prima: 

se nella vostra versione della tabella crop non avete il campo 'raw_fraction' ma due campi che si chiamano tipo 'frac_read_avail_water_max' e 'frac_read_avail_water_min' 
allora copiate i dati con attenzione: il valore che trovate in 'raw_fraction' va copiato uguale su entrambi.

- Units: Giulia aveva impostato la vite con meteo senza falda, e la pera con falda (e anche una pera senza falda, ma solo per fare confronti). 

Vi conviene impostare tre casi in questo modo:
- pera con falda
- vite con falda  
- vite senza falda

Questo perché abbiamo notato che un vigneto di swamp è leggermente rialzato rispetto alle aree limitrofe e quindi ci aspettiamo che la falda intervenga meno 
(Bertacchini id:2233 dal db di irrigazione del cbec).
 * 
 * */
public class Criteria {
	private static final Logger logger = LogManager.getLogger();

	final GenericClient sepaClient;
	final JSAP jsap;
	final String cmd;

	final String meteoDBPath;
	final String outputDBPath;
	final String forecastDBPath;
	
	Connection meteoDB;
	Connection outputDB;
	Connection forecastDB;

	final int days;

	final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

	public Criteria(String cmd, String host, String weatherDB, String outputDB, String soilDB, String cropDB,String unitsDB,int days,String forecastDB)
			throws SEPAProtocolException, SEPAPropertiesException, SEPASecurityException, SQLException,
			FileNotFoundException, IOException, URISyntaxException {
		jsap = new JSAP("criteria.jsap");

		if (host != null)
			jsap.setHost(host);
		sepaClient = new GenericClient(jsap, null);

		logger.info("SEPA_HOST: " + host);
		logger.info("CMD: " + cmd);
		
		logger.info("METEO DB: " + weatherDB);
		logger.info("OUTPUT DB: " + outputDB);
		logger.info("SOIL DB: " + soilDB);
		logger.info("CROP DB: " + cropDB);
		logger.info("UNITS DB: " + unitsDB);
		
		logger.info("FORECAST DAYS: " + days);
		logger.info("FORECAST DB: " + forecastDB);

		this.cmd = cmd;
		this.days = days;
		this.meteoDBPath = weatherDB;
		this.outputDBPath = outputDB;
		this.forecastDBPath = forecastDB;
		
		createIniFile( weatherDB,  outputDB,  soilDB,  cropDB, unitsDB, days, forecastDB);
	}

	/*
[software]
software="CRITERIA1D"

[project]
path=""
name="criteria-sepa-docker"
db_meteo="/data/weather.db"
db_soil="/data/soil.db"
db_crop="/data/crop.db"
db_units="/data/units.db"
db_output="/data/output.db"
db_forecast="/data/forecast.db"

[forecast]
isSeasonalForecast=false
isShortTermForecast=false
daysOfForecast=3
	 * */
	private void createIniFile(String weatherDB, String outputDB, String soilDB, String cropDB,String unitsDB,int days,String forecastDB) throws InvalidFileFormatException, IOException {
		File file = new File("criteria.ini");
		if (file.exists()) file.delete();
		file.createNewFile();
		
		Wini ini = new Wini(file);
      
		ini.put("software", "software", "CRITERIA1D");
        
		ini.put("project", "path", "");
		ini.put("project", "name", "criteria-sepa-docker");
		ini.put("project", "db_meteo", weatherDB);
		ini.put("project", "db_soil", soilDB);
		ini.put("project", "db_crop", cropDB);
		ini.put("project", "db_units", unitsDB);
		ini.put("project", "db_output", outputDB);
		if (forecastDB != null) ini.put("project", "db_forecast", forecastDB);
		
		ini.put("forecast", "isSeasonalForecast", false);
		if (days > 0) {
			ini.put("forecast", "isShortTermForecast", true);
			ini.put("forecast", "daysOfForecast", days);
		}
		else {
			ini.put("forecast", "isShortTermForecast", false);
			ini.put("forecast", "daysOfForecast", 0);
		}
		
        ini.store();
        
        logger.info("*** criteria.ini ***");
        File test = new File("criteria.ini");
        Scanner myReader = new Scanner(test);
        while (myReader.hasNextLine()) {
          String data = myReader.nextLine();
          logger.info(data);
        }
        myReader.close();
        logger.info("********************");
	}

	public void run(Calendar day) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException,
			SEPABindingsException, SQLException, IOException, InterruptedException {
		
		logger.info("Running Criteria SWAMP service: " + dateFormatter.format(day.getTime())
				+ " forecast interval (days): " + days);

		// Set input DBs
		logger.info("Set input...");
		setInput(day, days);
		
		// Run the model
		logger.info("Run Criteria...");
		run();

		// Get output from output DB (e.g., irrigation and LAI)
		getOutput(day, days, false);	
	}

	public void copyOutput(Calendar day, int days) throws SQLException {
		getOutput(day, days, true);
	}

	public void setWeatherDB(Calendar day) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException,
			SEPABindingsException, SQLException {
		// Set input DBs
		logger.info("Set input...");
		setInput(day, days);
	}

	private void run() throws IOException, InterruptedException {
		logger.info("*** Execute Criteria *** (" + cmd + " criteria.ini)");

		ProcessBuilder ps = new ProcessBuilder(cmd,"criteria.ini");
		ps.redirectErrorStream(true);

		Process pr = ps.start();

		BufferedReader in = new BufferedReader(new InputStreamReader(pr.getInputStream()));
		String line;
		while ((line = in.readLine()) != null) {
			logger.debug(line);
		}
		pr.waitFor();

		in.close();

		logger.info("*** Execute Criteria END ***");
	}

	private void setInput(Calendar day, int days) throws SEPAProtocolException, SEPASecurityException,
			SEPAPropertiesException, SEPABindingsException, SQLException {
		Calendar yesterday = new GregorianCalendar();
		yesterday.setTime(day.getTime());
		yesterday.add(Calendar.DAY_OF_MONTH, -1);

		meteoDB = DriverManager.getConnection("jdbc:sqlite:" + meteoDBPath);		
		if (forecastDBPath != null) forecastDB = DriverManager.getConnection("jdbc:sqlite:" + forecastDBPath); 
		
		updateObservedWeather(yesterday);
		updateWeatherForecasts(day, days);
		
		meteoDB.close();		
		if (forecastDBPath != null) forecastDB.close();
	}

	private void getOutput(Calendar day, int days, boolean copy) throws SQLException {
		logger.info("Get output...");
		outputDB = DriverManager.getConnection("jdbc:sqlite:" + outputDBPath);
		
		JsonObject outputs = jsap.getExtendedData().getAsJsonObject("outputs");
		
		for(Entry<String, JsonElement> output : outputs.entrySet()) {
			getOutputDB(day, days, output.getKey(), output.getValue().getAsJsonObject().get("property").getAsString(), output.getValue().getAsJsonObject().get("unit").getAsString(), copy);
		}
		
		outputDB.close();	
	}
	
	private String buildQueryString(String dbField,String table,String date) {
		String queryString = "select " + dbField + " from " + table + " where DATE='" + date + "'";
		if (dbField.equals("WC_PROFILE")) {
			queryString = "select WATER_CONTENT, ROOTDEPTH from " + table + " where DATE='" + date + "'";
		} else if (dbField.equals("DEFICIT_PROFILE")) {
			queryString = "select DEFICIT, ROOTDEPTH from " + table + " where DATE='" + date + "'";
		} else if (dbField.equals("THRESHOLD")) {
			queryString = "select WATER_CONTENT, ROOTDEPTH, RAW from " + table + " where DATE='" + date + "'";
		}
		return queryString;
	}
	
	private float computeOutput(String dbField,ResultSet rs) throws SQLException {
		float value;
		if (dbField.equals("WC_PROFILE")) {
			value = rs.getFloat("WATER_CONTENT") * rs.getFloat("ROOTDEPTH");
		} else if (dbField.equals("DEFICIT_PROFILE")) {
			value = rs.getFloat("DEFICIT") * rs.getFloat("ROOTDEPTH");
		} else if (dbField.equals("THRESHOLD")) {
			value = rs.getFloat("WATER_CONTENT") * rs.getFloat("ROOTDEPTH") - rs.getFloat("READILY_AW");
		} else value = rs.getFloat(dbField);
		return value;
	}

	private String getWaterTableFromDB(Calendar day, String table) throws SQLException {
		// Water table (from observed data)
		Calendar yesterday = new GregorianCalendar();
		yesterday.setTime(day.getTime());
		yesterday.add(Calendar.DAY_OF_MONTH, -1);

		String date = dateFormatter.format(yesterday.getTime());
		String queryString = "select watertable from " + table + " where DATE='" + date + "'";

		// Get record from CRITERIA DB
		Statement statement = meteoDB.createStatement();
		ResultSet rs = statement.executeQuery(queryString);

		String ret = null;
		if (rs.next()) {
			float value = rs.getFloat("watertable");
			ret = String.format("%.3f", value);
		}

		statement.close();

		return ret;
	}

	private void getOutputDB(Calendar day, int days, String dbField, String propertyUri, String unitUri, boolean copy)
			throws SQLException {
		JsonObject irrigations = jsap.getExtendedData().getAsJsonObject("places");

		logger.debug("getOutputDB Day:"+new SimpleDateFormat().format(day.getTime())+" Days:"+days+" DBField:"+dbField+" PropertyURI:"+propertyUri+" UnitURI:"+unitUri+" Copy:"+copy);
		
		Calendar stop = new GregorianCalendar();
		stop.setTime(day.getTime());
		stop.add(Calendar.DAY_OF_MONTH, days+1);

		String time = dateFormatter.format(day.getTime()) + "T00:00:00Z";

		for (Entry<String, JsonElement> placeEntry : irrigations.entrySet()) {
			String table = placeEntry.getValue().getAsString();
			String place = placeEntry.getKey();

			logger.debug("Table: "+table+" Place: "+place);
			
			Calendar forecast = new GregorianCalendar();
			forecast.setTime(day.getTime());

			while (forecast.before(stop)) {
				String date = dateFormatter.format(forecast.getTime());
				String pTime = date + "T00:00:00Z";

				// QUERY OUTPUTs
				String queryString = buildQueryString(dbField,table,date);

				logger.debug(queryString);

				// Get record from CRITERIA DB
				Statement statement = outputDB.createStatement();
				ResultSet rs = statement.executeQuery(queryString);

				while (rs.next()) {
					// COMPUTE OUTPUT
					float value = computeOutput(dbField, rs);

					logger.debug(
							"Results table: " + table + " date: " + date + " field: " + dbField + " value: " + value);

					Bindings fb = new Bindings();
					fb.addBinding("feature", new RDFTermURI(place));
					fb.addBinding("unit", new RDFTermURI(unitUri));
					fb.addBinding("property", new RDFTermURI(propertyUri));
					fb.addBinding("value", new RDFTermLiteral(String.format("%.2f", value), "xsd:number"));
					fb.addBinding("ptime", new RDFTermLiteral(pTime, "xsd:dateTime"));
					if (copy)
						fb.addBinding("time", new RDFTermLiteral(pTime, "xsd:dateTime"));
					else
						fb.addBinding("time", new RDFTermLiteral(time, "xsd:dateTime"));

					try {
						logger.info("ADD_OBSERVATION_FORECAST Feature of interest: " + place + " property: "
								+ propertyUri + " value: " + value + " time: " + time + " ptime: " + pTime);
						sepaClient.update("ADD_OBSERVATION_FORECAST", fb);
					} catch (SEPAProtocolException | SEPASecurityException | IOException | SEPAPropertiesException
							| SEPABindingsException e) {
						logger.error(e.getMessage());
					}
				}

				statement.close();

				forecast.add(Calendar.DAY_OF_MONTH, 1);
			}
		}
	}

	private void updateObservedWeather(Calendar day) throws SEPAProtocolException, SEPASecurityException,
			SEPAPropertiesException, SEPABindingsException, SQLException {

		logger.info("updateObservedWeather: " + dateFormatter.format(day.getTime()));

		JsonArray weatherStations = jsap.getExtendedData().getAsJsonArray("weather");

		String from = dateFormatter.format(day.getTime()) + "T00:00:00Z";
		String to = dateFormatter.format(day.getTime()) + "T23:59:59Z";

		Bindings fBindings = new Bindings();
		fBindings.addBinding("from", new RDFTermLiteral(from, "xsd:dateTime"));
		fBindings.addBinding("to", new RDFTermLiteral(to, "xsd:dateTime"));

		for (JsonElement station : weatherStations) {
			// Temperature
			String tmin = null;
			String tmax = null;
			String tavg = null;
			String temperature = station.getAsJsonObject().get("temperatureUri").getAsString();
			fBindings.addBinding("observation", new RDFTermURI(temperature));

			Response ret = sepaClient.query("WEATHER_TEMPERATURE", fBindings);

			if (ret.isError()) {
				logger.error("Failed to query temperature " + temperature + " from: " + from + " to: " + to);
			} else {
				QueryResponse res = (QueryResponse) ret;
				if (res.getBindingsResults().isEmpty())
					logger.error("No results for temperature " + temperature + " from: " + from + " to: " + to);
				else {
					tmax = res.getBindingsResults().getBindings().get(0).getValue("max");
					tmin = res.getBindingsResults().getBindings().get(0).getValue("min");
					tavg = res.getBindingsResults().getBindings().get(0).getValue("avg");
					tavg = String.format("%.2f", Float.parseFloat(tavg));

					logger.info("From: " + from + " to: " + to + " Weather temperature: " + temperature + " <" + tmin
							+ " " + tavg + " " + tmax + ">");
				}
			}

			// Precipitation
			String prec = null;
			String precipitation = station.getAsJsonObject().get("precipitationUri").getAsString();
			fBindings.addBinding("observation", new RDFTermURI(precipitation));

			ret = sepaClient.query("WEATHER_PRECIPITATION", fBindings);

			if (ret.isError()) {
				logger.error("Failed to query precipitation " + precipitation + " from: " + from + " to: " + to);
			} else {
				QueryResponse res = (QueryResponse) ret;
				if (res.getBindingsResults().isEmpty())
					logger.error("No results for precipitation " + precipitation + " from: " + from + " to: " + to);
				else {
					prec = res.getBindingsResults().getBindings().get(0).getValue("sum");
					prec = String.format("%.2f", Float.parseFloat(prec));

					logger.info("From: " + from + " to: " + to + " Weather precipitation: " + precipitation + " <"
							+ prec + ">");
				}
			}

			// Water table
			String wt = null;
			String watertable = station.getAsJsonObject().get("watertableUri").getAsString();
			boolean useWt = station.getAsJsonObject().get("useWaterTable").getAsBoolean();
			String dbTableName = station.getAsJsonObject().get("table").getAsString();

			if (useWt) {
				fBindings.addBinding("observation", new RDFTermURI(watertable));

				ret = sepaClient.query("WATER_TABLE", fBindings);

				if (ret.isError()) {
					logger.error("Failed to query water table " + watertable + " from: " + from + " to: " + to);
				} else {
					QueryResponse res = (QueryResponse) ret;
					if (res.getBindingsResults().isEmpty()) {
						logger.error("No results for water table " + watertable + " from: " + from + " to: " + to);

						// NO WATER TABLE FOUND: use water table from DB of previous day, if any
						wt = getWaterTableFromDB(day, dbTableName);

					} else {
						wt = res.getBindingsResults().getBindings().get(0).getValue("wt");
						wt = String.format("%.2f", Float.parseFloat(wt));

						logger.info("From: " + from + " to: " + to + " Water table: " + watertable + " <" + wt + ">");
					}
				}
			}

			updateInputDB(meteoDB,dbTableName, dateFormatter.format(day.getTime()), tmin, tmax, tavg, prec, wt);
		}
	}

	private void updateWeatherForecasts(Calendar day, int days) throws SEPAProtocolException, SEPASecurityException,
			SEPAPropertiesException, SEPABindingsException, SQLException {
		Calendar endCalendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
		endCalendar.setTime(day.getTime());
		endCalendar.add(Calendar.DAY_OF_MONTH, days);

		Calendar forecastDay = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
		forecastDay.setTime(day.getTime());

		while (forecastDay.before(endCalendar)) {
			updateWeatherForecasts(day, forecastDay);
			forecastDay.add(Calendar.DAY_OF_MONTH, 1);
		}
	}

	private void updateWeatherForecasts(Calendar day, Calendar forecastDay) throws SEPAProtocolException,
			SEPASecurityException, SEPAPropertiesException, SEPABindingsException, SQLException {

		logger.info("updateWeatherForecasts: " + dateFormatter.format(forecastDay.getTime()));

		JsonArray weatherStations = jsap.getExtendedData().getAsJsonArray("weather");

		String dayString = dateFormatter.format(day.getTime());
		String forecastString = dateFormatter.format(forecastDay.getTime());

		Bindings fBindings = new Bindings();
		fBindings.addBinding("day", new RDFTermLiteral(dayString));
		fBindings.addBinding("forecast", new RDFTermLiteral(forecastString));

		for (JsonElement station : weatherStations) {
			String place = station.getAsJsonObject().get("stationUri").getAsString();

			fBindings.addBinding("place", new RDFTermURI(place));

			// Temperature
			String tmin = null;
			String tmax = null;
			String tavg = null;

			Response ret = sepaClient.query("WEATHER_TEMPERATURE_FORECAST", fBindings);
			if (ret.isError()) {
				logger.error("Failed to query temperature forecast " + place + " day: " + dayString + " forecast: "
						+ forecastString);
			} else {
				QueryResponse res = (QueryResponse) ret;
				if (res.getBindingsResults().isEmpty())
					logger.error("No results for query temperature forecast " + place + " day: " + dayString
							+ " forecast: " + forecastString);
				else {
					tmax = res.getBindingsResults().getBindings().get(0).getValue("max");
					tmin = res.getBindingsResults().getBindings().get(0).getValue("min");
					tavg = res.getBindingsResults().getBindings().get(0).getValue("avg");
					// tavg = String.format("%.2f", Float.parseFloat(tavg));

					logger.info("Temperature forecast " + place + " day: " + dayString + " forecast: " + forecastString
							+ " place: " + place + " <" + tmin + " " + tavg + " " + tmax + ">");
				}
			}

			// Precipitation
			String prec = null;

			ret = sepaClient.query("WEATHER_PRECIPITATION_FORECAST", fBindings);
			if (ret.isError()) {
				logger.error("Failed to query precipitation forecast " + place + " day: " + dayString + " forecast: "
						+ forecastString + " place: " + place);
			} else {
				QueryResponse res = (QueryResponse) ret;
				if (res.getBindingsResults().isEmpty())
					logger.error("no results for query precipitation forecast " + place + " day: " + dayString
							+ " forecast: " + forecastString + " place: " + place);
				else {
					prec = res.getBindingsResults().getBindings().get(0).getValue("sum");
					// prec = String.format("%.2f", Float.parseFloat(prec));

					logger.info("Precipitation forecast " + place + " day: " + dayString + " forecast: "
							+ forecastString + " place: " + place + " <" + prec + ">");
				}
			}

			// Watertable
			String wt = null;
			String dbTableName = station.getAsJsonObject().get("table").getAsString();
			boolean useWt = station.getAsJsonObject().get("useWaterTable").getAsBoolean();

			if (useWt)
				wt = getWaterTableFromDB(day, dbTableName);

			updateInputDB(forecastDB,dbTableName, dateFormatter.format(forecastDay.getTime()), tmin, tmax, tavg, prec, wt);
		}
	}

	private void updateInputDB(Connection db,String table, String date, String tmin, String tmax, String tavg, String prec,
			String waterTable) throws SQLException {

		logger.info("Set input DB: " + meteoDBPath + " Table: " + table + " Date: " + date + " Tmin: " + tmin
				+ " Tmax: " + tmax + " Tavg: " + tavg + " Prec: " + prec);

		Statement statement = db.createStatement();
		statement.setQueryTimeout(2);

		// DELETE
		String queryString = "select * from " + table + " where date='" + date + "'";
		ResultSet rs = statement.executeQuery(queryString);
		while (rs.next()) {
			logger.info(rs.getRow()+" "+rs.getString("date")+" "+rs.getFloat("tmin")+" "+rs.getFloat("tmax")+ " "+rs.getFloat("tavg"));
//			if (rs.getDate("date").equals(date)) {
				logger.info("DELETE FROM " + table + " WHERE DATE ='" + date + "'");
				statement.executeUpdate("delete from " + table + " where date ='" + date + "'");
//			}
		}

		// INSERT
		String values = null;
		if (waterTable != null)
			values = "'" + date + "','" + tmin + "','" + tmax + "','" + tavg + "','" + prec + "','" + waterTable + "'";
		else
			values = "'" + date + "','" + tmin + "','" + tmax + "','" + tavg + "','" + prec + "'";

		if (waterTable != null) {
			logger.info("INSERT INTO " + table + " (date,tmin,tmax,tavg,prec,watertable) VALUES (" + values + ")");
			statement.executeUpdate(
					"insert into " + table + " (date,tmin,tmax,tavg,prec,watertable) values (" + values + ")");

		} else {
			logger.info("INSERT INTO " + table + " (date,tmin,tmax,tavg,prec) VALUES (" + values + ")");
			statement.executeUpdate("insert into " + table + " (date,tmin,tmax,tavg,prec) values(" + values + ")");

		}

		statement.close();
	}
}
