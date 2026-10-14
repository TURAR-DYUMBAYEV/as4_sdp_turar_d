package ecommerce.subsystemb;
import ecommerce.exception.StageException;
public final class WarehousePicker {
    public void validate(String id,String product,int quantity,boolean stationUnavailable) {
        if(id==null || id.isBlank() || product==null || product.isBlank() || quantity<=0)
            throw new StageException("Invalid warehouse picking request.");
        if(stationUnavailable) throw new StageException("Packing station is unavailable.");
    }
}
