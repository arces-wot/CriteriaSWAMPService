package it.unibo.arces.wot.sepa.apps.swamp;

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

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class Criteria implements Closeable {
	private static final Logger logger = LogManager.getLogger();

	final GenericClient sepaClient;
	final Connection weatherDB;
	final Connection outputDB;
	final JSAP jsap;
	final String cmd;
	
	final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

	public Criteria(String weatherDbPath, String irrigationDbPath, String cmd) throws SEPAProtocolException,
			SEPAPropertiesException, SEPASecurityException, SQLException, FileNotFoundException, IOException {
		jsap = new JSAP("base.jsap");
		jsap.read("criteria.jsap", true);

		sepaClient = new GenericClient(jsap);
		weatherDB = DriverManager.getConnection("jdbc:sqlite:" + weatherDbPath);
		outputDB = DriverManager.getConnection("jdbc:sqlite:" + irrigationDbPath);
		this.cmd = cmd;
	}

	public void run(Calendar day, int days) throws SEPAProtocolException, SEPASecurityException,
			SEPAPropertiesException, SEPABindingsException, SQLException, IOException, InterruptedException {
		
		logger.info("Running: "+ dateFormatter.format(day.getTime())+ " forecast interval (days): "+days);
		
		// Set input DBs
		setInput(day, days);
	
		// Run the model
		run();
		
		// Get output from output DB (e.g., irrigation and LAI)
		getOutput(day, days);
	}

	private void run() throws IOException, InterruptedException {
		Process process = Runtime.getRuntime().exec(cmd);
		while (process.isAlive()) {
			Thread.sleep(1000);
		}
	}
	
	private void setInput(Calendar day,int days) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, SQLException {
		Calendar yesterday = new GregorianCalendar();
		yesterday.setTime(day.getTime());
		yesterday.add(Calendar.DAY_OF_MONTH, -1);
		
		updateObservedWeather(yesterday);
		updateWeatherForecasts(day, days);
	}

	private void getOutput(Calendar day,int days) throws SQLException {
		getOutputDB(day, days, "IRRIGATION","swamp:IrrigationNeeds","unit:Millimeter");
		getOutputDB(day, days, "LAI","swamp:LeafAreaIndex","unit:Number");
	}	
	
	private void getOutputDB(Calendar day, int days,String dbField,String propertyUri,String unitUri) throws SQLException {
		JsonObject irrigations = jsap.getExtendedData().getAsJsonObject("places");

		Calendar stop = new GregorianCalendar();
		stop.setTime(day.getTime());
		stop.add(Calendar.DAY_OF_MONTH, days);
		
		String time = dateFormatter.format(day.getTime()) + "T00:00:00Z";
		
		for (Entry<String, JsonElement> placeEntry : irrigations.entrySet()) {
			String table = placeEntry.getValue().getAsString();
			String place = placeEntry.getKey();

			Calendar forecast = new GregorianCalendar();
			forecast.setTime(day.getTime());
			
			while (forecast.before(stop)) {
				String date = dateFormatter.format(forecast.getTime());
				String pTime = date + "T00:00:00Z";
				String queryString = "select "+dbField+" from " + table + " where DATE='" + date + "'";
				
				logger.debug(queryString);
				
				// Get record from CRITERIA DB
				Statement statement = outputDB.createStatement();
				ResultSet rs = statement.executeQuery(queryString);
				
				while (rs.next()) {
					float value = rs.getFloat(dbField);
					logger.debug("Results table: " + table + " date: " + date + " field: "+dbField+ " value: " + value);	

					Bindings fb = new Bindings();
					fb.addBinding("feature", new RDFTermURI(place));
					fb.addBinding("unit", new RDFTermURI(unitUri));
					fb.addBinding("property", new RDFTermURI(propertyUri));
					fb.addBinding("value", new RDFTermLiteral(String.format("%.2f", value), "xsd:number"));
					fb.addBinding("ptime", new RDFTermLiteral(pTime, "xsd:dateTime"));
					fb.addBinding("time", new RDFTermLiteral(time, "xsd:dateTime"));
					
					try {
						logger.info("ADD_OBSERVATION_FORECAST foi: " + place + " property: "+propertyUri+" value: " + value + " time: " + time + " ptime: "+pTime);
						sepaClient.update("ADD_OBSERVATION_FORECAST", fb, 5000);
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

		logger.info("updateObservedWeather: "+ dateFormatter.format(day.getTime()));
		
		JsonArray weatherStations = jsap.getExtendedData().getAsJsonArray("weather");

		for (JsonElement station : weatherStations) {
			String temperature = station.getAsJsonObject().get("temperatureUri").getAsString();
			String precipitation = station.getAsJsonObject().get("precipitationUri").getAsString();
			String table = station.getAsJsonObject().get("table").getAsString();

			String from = dateFormatter.format(day.getTime()) + "T00:00:00Z";
			String to = dateFormatter.format(day.getTime()) + "T23:59:59Z";

			Bindings fBindings = new Bindings();
			fBindings.addBinding("observation", new RDFTermURI(temperature));
			fBindings.addBinding("from", new RDFTermLiteral(from, "xsd:dateTime"));
			fBindings.addBinding("to", new RDFTermLiteral(to, "xsd:dateTime"));

			String tmin = "?";
			String tmax = "?";
			String tavg = "?";
			String prec = "?";

			Response ret = sepaClient.query("WEATHER_TEMPERATURE", fBindings, 10000);
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

			fBindings.addBinding("observation", new RDFTermURI(precipitation));
			ret = sepaClient.query("WEATHER_PRECIPITATION", fBindings, 10000);
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

			updateWeatherDB(table, dateFormatter.format(day.getTime()), tmin, tmax, tavg, prec, "0", "0");
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
			updateWeatherForecasts(day,forecastDay);
			forecastDay.add(Calendar.DAY_OF_MONTH, 1);
		}
	}

	private void updateWeatherForecasts(Calendar day,Calendar forecastDay) throws SEPAProtocolException, SEPASecurityException,
			SEPAPropertiesException, SEPABindingsException, SQLException {

		logger.info("updateWeatherForecasts: "+dateFormatter.format(forecastDay.getTime()));
		
		JsonArray weatherStations = jsap.getExtendedData().getAsJsonArray("weather");

		for (JsonElement station : weatherStations) {
			String place = station.getAsJsonObject().get("forecastUri").getAsString();
			String table = station.getAsJsonObject().get("table").getAsString();

			String dayString = dateFormatter.format(day.getTime());
			String forecastString = dateFormatter.format(forecastDay.getTime());

			Bindings fBindings = new Bindings();
			fBindings.addBinding("place", new RDFTermURI(place));
			fBindings.addBinding("day", new RDFTermLiteral(dayString));
			fBindings.addBinding("forecast", new RDFTermLiteral(forecastString));

			String tmin = "?";
			String tmax = "?";
			String tavg = "?";
			String prec = "?";

			Response ret = sepaClient.query("WEATHER_TEMPERATURE_FORECAST", fBindings, 10000);
			if (ret.isError()) {
				logger.error("Failed to query temperature forecast " + place + " day: " + dayString + " forecast: " + forecastString);
			} else {
				QueryResponse res = (QueryResponse) ret;
				if (res.getBindingsResults().isEmpty())
					logger.error("No results for query temperature forecast " + place + " day: " + dayString + " forecast: " + forecastString);
				else {
					tmax = res.getBindingsResults().getBindings().get(0).getValue("max");
					tmin = res.getBindingsResults().getBindings().get(0).getValue("min");
					tavg = res.getBindingsResults().getBindings().get(0).getValue("avg");
					tavg = String.format("%.2f", Float.parseFloat(tavg));

					logger.info("Temperature forecast " + place + " day: " + dayString + " forecast: " + forecastString +" place: " + place + " <" + tmin
							+ " " + tavg + " " + tmax + ">");
				}
			}

			ret = sepaClient.query("WEATHER_PRECIPITATION_FORECAST", fBindings, 10000);
			if (ret.isError()) {
				logger.error("Failed to query precipitation forecast " + place + " day: " + dayString + " forecast: " + forecastString +" place: " + place);
			} else {
				QueryResponse res = (QueryResponse) ret;
				if (res.getBindingsResults().isEmpty())
					logger.error("no results for query precipitation forecast " + place + " day: " + dayString + " forecast: " + forecastString +" place: " + place);
				else {
					prec = res.getBindingsResults().getBindings().get(0).getValue("sum");
					prec = String.format("%.2f", Float.parseFloat(prec));

					logger.info("Precipitation forecast " + place + " day: " + dayString + " forecast: " + forecastString +" place: " + place + " <" + prec + ">");
				}
			}

			updateWeatherDB(table, dateFormatter.format(forecastDay.getTime()), tmin, tmax, tavg, prec, "0", "0");
		}
	}

	private void updateWeatherDB(String table, String date, String tmin, String tmax, String tavg, String prec,
			String etp, String waterTable) throws SQLException {
		Statement statement = weatherDB.createStatement();
		statement.setQueryTimeout(30); // set timeout to 30 sec.

		String values = "'" + date + "','" + tmin + "','" + tmax + "','" + tavg + "','" + prec + "','" + etp + "','"
				+ waterTable + "'";
		statement.executeUpdate("delete from " + table + " where date ='" + date + "'");
		statement.executeUpdate("insert into " + table + " values(+" + values + ")");
	}

	@Override
	public void close() throws IOException {
		try {
			weatherDB.close();
		} catch (SQLException e) {
			logger.error(e.getMessage());
		}
		try {
			outputDB.close();
		} catch (SQLException e) {
			logger.error(e.getMessage());
		}
	}

}
