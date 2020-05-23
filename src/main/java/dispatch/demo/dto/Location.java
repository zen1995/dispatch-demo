package dispatch.demo.dto;

import lombok.Data;

/**
 * @author eleme.demo
 */
@Data
public class Location {
    private Double latitude;
    private Double longitude;
    @Override
    public Location clone(){
        Location loc = new Location();
        loc.setLatitude(latitude);
        loc.setLongitude(longitude);
        return loc;
    }
    public double distanceTo(final Location loc){
        return Math.sqrt(Math.pow(loc.getLatitude()-getLatitude(),2)+Math.pow(loc.getLongitude()-getLongitude(),2));
    }
}
