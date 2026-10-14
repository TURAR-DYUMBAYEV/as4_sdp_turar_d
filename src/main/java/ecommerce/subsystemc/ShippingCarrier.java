package ecommerce.subsystemc;
import ecommerce.exception.StageException;
public final class ShippingCarrier {
    public String register(String id,boolean unavailable) {
        if(unavailable) throw new StageException("Shipping carrier is unavailable after payment.");
        return "TRACK-" + id;
    }
}
