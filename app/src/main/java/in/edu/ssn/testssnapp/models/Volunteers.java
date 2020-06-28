package in.edu.ssn.testssnapp.models;

import com.sahurjt.objectcsv.annotations.CsvModel;
import com.sahurjt.objectcsv.annotations.CsvParameter;

@CsvModel(headerPresent = true)
public class Volunteers {
    @CsvParameter(value = "emailid")
    public String emailid;
    @CsvParameter(value = "volunteer")
    public String volunteer;
    @CsvParameter(value = "busno")
    public String busno;

    public String getEmailid(){
        return emailid;
    }

    public String getVolunteer(){
        return volunteer;
    }

    public String getBusno(){ return busno; }
}
