import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicOptionPaneUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class Client extends JFrame implements Runnable, ActionListener, ListSelectionListener
{
    /**
     * Program uses Wnuderground API.
     * Restriction of current key is 10 calls per minute and 500 calls per day.
     */

    Thread thread; //this thread
    private final static String API_KEY = "58c505a2e379ef97";
    private final static int CALLS_PER_MINUTE = 5; //number of allowed calls to api per minute
    JPanel topPanel; //navigation panel on top
    JPanel contentPanel = new JPanel(); //panel with weather content
    JButton currentB = new JButton("Current weather"), sateliteB = new JButton("Satellite"),
            radarB = new JButton("Radar"), satradB = new JButton("Satellite + Radar"),
            compareB = new JButton("Compare dates"); //objects for buttons on top panel
    JList<String> locationSelect; //list with cities selection
    private volatile String location, //what location to display now
            queryStr, //URL query to API
            whatDisplay = "Current weather"; //what kind of content to display now
    private volatile int firstMonth = -1, firstDay = -1, howMore = -1, //variables for setting up URL for dates comparison
            callsLastMinute = 0; //how many calls were made to API for last minute
    private volatile long minuteCounter = -1; //time stamp for counting minutes

    /**
     * Program enter point.
     * @param args
     */
    public static void main(String args[])
    {
        new Client();
    }

    /**
     * Class constructor. Set up window, fill it with UI
     * and run thread to get current weather.
     */
    Client()
    {
        super("Weather");
        setLayout(new BorderLayout());
        ((JComponent)getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        //stop program when window is closed
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        topPanel = new JPanel();
        //make list of cities
        DefaultListModel<String> locations = new DefaultListModel<>();
        locations.addElement("Slovenia/Maribor");
        locations.addElement("Ukraine/Sumy");
        locationSelect = new JList<>(locations);
        locationSelect.addListSelectionListener(this);
        locationSelect.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        locationSelect.setSelectedIndex(0);
        location = locationSelect.getSelectedValue();
        topPanel.setLayout(new BasicOptionPaneUI.ButtonAreaLayout(true, 10));
        //add this object as listener for actions on buttons
        currentB.addActionListener(this);
        sateliteB.addActionListener(this);
        radarB.addActionListener(this);
        satradB.addActionListener(this);
        compareB.addActionListener(this);
        topPanel.add(currentB);
        topPanel.add(sateliteB);
        topPanel.add(radarB);
        topPanel.add(satradB);
        topPanel.add(compareB);
        topPanel.add(locationSelect);
        add(topPanel, "North");
        thread = new Thread(this);
        thread.start();
        pack();
        setSize(getWidth(), 420); //height which will be enough for content on any frame
        setResizable(false);
        setVisible(true); //display window
    }

    /**
     * Invoke new thread to make call to Wunderground API to get
     * JSON or GIF file (depends on selected action)
     * and display data once it is downloaded.
     */
    @Override
    public void run()
    {
        location = locationSelect.getSelectedValue(); //get selected city
        try
        {
            queryStr = makeQueryStr(); //try to get URL for call
        }
        catch(Exception e)
        {
            //may occur if user makes too many calls to API
            JOptionPane.showMessageDialog(this, e.getMessage());
            return;
        }
        System.out.println(queryStr + "\n");
        if("Satellite".equals(whatDisplay) || "Radar".equals(whatDisplay) || "Satellite + Radar".equals(whatDisplay))
        {
            //display image
            contentPanel.removeAll(); //remove all components
            try
            {
                contentPanel.add(new JLabel(new ImageIcon(new URL(queryStr))));
                add(contentPanel, "South");
                repaint();
                revalidate();
            }
            catch (MalformedURLException e)
            {
                e.printStackTrace();
            }
            return;
        }
        else if("Compare dates".equals(whatDisplay))
        {
            //display frame where user can specify what time period he
            //wants to be displayed on date comparison.
            contentPanel.removeAll(); //remove all components
            JComboBox<Integer> firstDayBox = getBox(31);
            JComboBox<Integer> firstMonthBox = getBox(12);
            JComboBox<Integer> howMoreBox = getBox(30);
            contentPanel.add(new JLabel("Select day of starting limit:"));
            contentPanel.add(firstDayBox);
            contentPanel.add(new JLabel("Select month of starting limit:"));
            contentPanel.add(firstMonthBox);
            contentPanel.add(new JLabel("How many days more is end limit? (no more that 30):"));
            contentPanel.add(howMoreBox);
            add(contentPanel, "South");
            //display changes
            repaint();
            revalidate();
            //listeners must be added after combo boxes
            //are set to panel because actionPerformed
            //method gets invoked also when box is set.
            //It leads to unwanted values assignment.
            firstDayBox.addActionListener(new ActionListener()
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    firstDay = (Integer) firstDayBox.getSelectedItem();
                    tryMakeComparison();
                }
            });
            firstMonthBox.addActionListener(new ActionListener()
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    firstMonth = (Integer) firstMonthBox.getSelectedItem();
                    tryMakeComparison();
                }
            });
            howMoreBox.addActionListener(new ActionListener()
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    howMore = (Integer) howMoreBox.getSelectedItem();
                    tryMakeComparison();
                }
            });
            return;
        }
        URL query = null;
        HttpURLConnection conn = null;
        JSONObject result = null;
        try
        {
            query = new URL(queryStr);
            conn = (HttpURLConnection) query.openConnection();
            conn.setRequestMethod("GET");
            //get json file from API
            result = (JSONObject) new JSONParser().parse(new InputStreamReader(conn.getInputStream()));
            //put weather data on screen (not images)
            if("Current weather".equals(whatDisplay)) displayCurrent(result);
            else displayComparison(result);
        }
        catch (IOException | ParseException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Handle buttons press.
     * @param e event object
     */
    @Override
    public void actionPerformed(ActionEvent e)
    {
        firstDay = -1; firstMonth = -1; howMore = -1; //reset values for date comparison
        whatDisplay = e.getActionCommand(); //what type of content user wants to see
        System.out.println(e.getActionCommand());
        thread = new Thread(this); //thread can't be invoked more than one time
        thread.start(); //actualize data
    }

    /**
     * Handle list selection.
     * @param e list event object
     */
    @Override
    public void valueChanged(ListSelectionEvent e)
    {
        if(e.getValueIsAdjusting())
        {
            int selectIndex = locationSelect.getSelectedIndex();
            location = locationSelect.getModel().getElementAt(selectIndex);
            System.out.println("\n" + location);
            thread = new Thread(this);
            thread.start();
        }
    }

    /**
     * Fetch link for making call to API depending on what user want's to see.
     * @return
     */
    private String makeQueryStr() throws Exception
    {
        tryCall(); //be sure that user doesn't make more calls per minute than allowed
        int delay = 40, interval = 30;
        if("Current weather".equals(whatDisplay)) //current weather information
        {
            return "http://api.wunderground.com/api/" + API_KEY
                    + "/astronomy/conditions/q/" + location + ".json";
        }
        else if("Satellite".equals(whatDisplay)) //animated satellite image
        {
            return "http://api.wunderground.com/api/" + API_KEY
                    + "/animatedsatellite/q/" + location
                    + ".gif?timelabel=1&timelabel.y=10&num=5&delay=" + delay + "&interval=" + interval;
        }
        else if("Radar".equals(whatDisplay)) //static radar image (animated radar works with problems)
        {
            return "http://api.wunderground.com/api/" + API_KEY
                    + "/radar/q/" + location
                    + ".gif?timelabel=1&timelabel.y=10&width=280&height=280&newmaps=1";
        }
        else if("Satellite + Radar".equals(whatDisplay)) //animated combined image
        {
            return "http://api.wunderground.com/api/" + API_KEY
                    + "/radar/animatedsatellite/q/" + location
                    + ".gif?num=8&delay=" + delay + "&interval=" + interval;
        }
        else if("Compare dates result".equals(whatDisplay)) //comparison between two dates
        {
            String formatedDate = formatDate();
            if(formatedDate == null) return null;
            return "http://api.wunderground.com/api/" + API_KEY
                    + "/planner_" + formatedDate + "/q/" + location
                    + ".json";
        }
        return null;
    }

    /**
     * Display information about current weather situation in
     * selected location.
     * @param json result of call to API already contains needed data
     */
    private void displayCurrent(JSONObject json)
    {
        contentPanel.removeAll(); //remove all components
        if(json == null)
        {
            contentPanel.add(new JLabel("Connection error."));
            add(contentPanel, "South");
            repaint();
            revalidate();
            return;
        }
        JSONObject current = (JSONObject) json.get("current_observation");
        //all fields are added below
        contentPanel.add(new JLabel("Current time in " + ((JSONObject)current.get("display_location")).get("full") + ": " + current.get("local_time_rfc822")));
        contentPanel.add(new JLabel("Last weather check: " + current.get("observation_time_rfc822")));
        try
        {
            contentPanel.add(new JLabel(new ImageIcon(new URL((String) current.get("icon_url")))));
        }
        catch (MalformedURLException e)
        {
            contentPanel.add(new JLabel("Error getting image"));
        }
        contentPanel.add(new JLabel((String) current.get("temperature_string")));
        contentPanel.add(new JLabel("Weather: " + current.get("weather")));
        contentPanel.add(new JLabel("Humidity: " + current.get("relative_humidity")));
        contentPanel.add(new JLabel("Wind: " + current.get("wind_string")));
        contentPanel.add(new JLabel("Pressure: " + current.get("pressure_mb") + " mbar"));
        contentPanel.add(new JLabel("Dewpoint: " + current.get("dewpoint_string")));
        contentPanel.add(new JLabel("Feels like: " + current.get("feelslike_string")));
        contentPanel.add(new JLabel("Visibility: " + current.get("visibility_km") + " km"));
        JSONObject astronomy = (JSONObject) json.get("moon_phase");
        JSONObject sunrise = (JSONObject)astronomy.get("sunrise");
        contentPanel.add(new JLabel("Sunrise: " + sunrise.get("hour") + ":" + sunrise.get("minute")));
        JSONObject sunset = (JSONObject)astronomy.get("sunset");
        contentPanel.add(new JLabel("Sunset: " + sunset.get("hour") + ":" + sunset.get("minute")));
        contentPanel.add(new JLabel(astronomy.get("phaseofMoon") + " moon: "
                + astronomy.get("percentIlluminated") + "% illuminated"));
        add(contentPanel, "South");
        //actualize frame
        repaint();
        revalidate();
    }

    /**
     * Display information difference between two selected dates.
     * @param json response from API already contains needed data
     */
    private void displayComparison(JSONObject json)
    {
        contentPanel.removeAll(); //remove all components
        if(json == null)
        {
            contentPanel.add(new JLabel("Connection error."));
            add(contentPanel, "South");
            repaint();
            revalidate();
            return;
        }
        JSONObject trip = (JSONObject) json.get("trip");
        contentPanel.add(new JLabel((String) trip.get("title")));
        contentPanel.add(new JLabel("Start date: " + getDeepJson(json, "trip.period_of_record.date_start.date.date").get("pretty")));
        contentPanel.add(new JLabel("End date: " + getDeepJson(json, "trip.period_of_record.date_end.date.date").get("pretty")));
        contentPanel.add(new JLabel("Highest temperature: " + getDeepJson(json, "trip.temp_high.max.F").get("F") + " F ("
                + getDeepJson(json, "trip.temp_high.max.C").get("C") + " C)"));
        contentPanel.add(new JLabel("Lowest temperature: " + getDeepJson(json, "trip.temp_low.min.F").get("F") + " F ("
                + getDeepJson(json, "trip.temp_low.min.C").get("C") + " C)"));
        contentPanel.add(new JLabel("Precipitation: " + getDeepJson(json, "trip.precip.avg.cm").get("cm") + " cm"));
        contentPanel.add(new JLabel("Dew point high: " + getDeepJson(json, "trip.dewpoint_high.max.F").get("F") + " F ("
                + getDeepJson(json, "trip.dewpoint_high.max.C").get("C") + " C)"));
        contentPanel.add(new JLabel("Dew point low: " + getDeepJson(json, "trip.dewpoint_low.min.F").get("F") + " F ("
                + getDeepJson(json, "trip.dewpoint_low.min.C").get("C") + " C)"));
        add(contentPanel, "South");
        repaint();
        revalidate();
    }

    /**
     * Support function to build combo box (openable selection list)
     * with numeric selection from 1 to <i>numbers</i>
     * @param numbers top selection in combo box
     * @return combo box object with needed options
     */
    public JComboBox<Integer> getBox(int numbers)
    {
        Integer model[] = new Integer[numbers];
        for(int i = 1; i <= numbers; i++)
        {
            model[i-1] = i;
        }
        JComboBox<Integer> result = new JComboBox<>(model);
        return result;
    }

    /**
     * Get comparison between two dates only if they were set by user.
     */
    private void tryMakeComparison()
    {
        if(firstDay == -1 || firstMonth == -1 || howMore == -1) return;
        contentPanel.removeAll();
        whatDisplay = "Compare dates result";
        System.out.println(whatDisplay);
        thread = new Thread(this); //get comparison and display it
        thread.start();
    }

    /**
     * Return date in required by API format MMDDMMDD.
     * @return string with formatted date
     */
    private String formatDate()
    {
        if(firstDay == -1 || firstMonth == -1 || howMore == -1) return null;
        Calendar calToday = Calendar.getInstance();
        int maxDayInMonth = calToday.getActualMaximum(Calendar.DAY_OF_MONTH);
        if(firstDay > maxDayInMonth) firstDay = maxDayInMonth; //prevent input of invalid day
        Calendar calNextMonth = Calendar.getInstance();
        calNextMonth.set(Calendar.DAY_OF_MONTH, 15);
        int nextM = firstMonth + 1;
        if(nextM > 12) nextM = 1;
        calNextMonth.set(Calendar.MONTH, nextM - 1);
        int maxDayInNextMonth = calNextMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
        int secondDay = firstDay + howMore;
        int secondMonth = firstMonth;
        if(secondDay > maxDayInMonth)
        {
            secondDay = secondDay - maxDayInMonth;
            secondMonth += 1;
            if(secondMonth > 12) secondMonth = 1;
            if(secondDay > maxDayInNextMonth)
            {
                secondDay = secondDay - maxDayInNextMonth;
                secondMonth += 1;
                if (secondMonth > 12) secondMonth = 1;
            }
        }
        return String.format("%02d%02d%02d%02d", firstMonth, firstDay, secondMonth, secondDay);
    }

    /**
     * See if user doesn't make too much calls to API.
     * @throws Exception thrown on every time user tries to
     * make call to API after reaching limit
     */
    private void tryCall() throws Exception
    {
        if(System.currentTimeMillis() - minuteCounter > 60000)
        {
            //reset counter every 60 seconds
            minuteCounter = System.currentTimeMillis();
            callsLastMinute = 0;
        }
        if(callsLastMinute > CALLS_PER_MINUTE) throw new Exception("Too much calls to API ("
                + (60 - (System.currentTimeMillis() - minuteCounter)/1000) + " sec left)");
        callsLastMinute++;
    }

    /**
     * Selector of JSON Object's children.
     * Separate name of objects with dot to bury deeper into object.
     * @param request JSON elements names separated with dots
     * @return requested JSON element
     */
    public static JSONObject getDeepJson(JSONObject parent, String request)
    {
        String splited[] = request.split("\\.", 2);
        if(splited.length < 2) return parent;
        return getDeepJson((JSONObject) parent.get(splited[0]), splited[1]);
    }
}
