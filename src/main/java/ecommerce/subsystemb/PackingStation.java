package ecommerce.subsystemb;
public final class PackingStation {
    private final WarehouseRepository repository;
    private final WarehousePicker picker;
    public PackingStation(WarehouseRepository repository,WarehousePicker picker) { this.repository=repository; this.picker=picker; }
    public PackingJob pack(String id,String product,int quantity,boolean unavailable) {
        picker.validate(id,product,quantity,unavailable);
        return repository.pack(id,product,quantity);
    }
    public void cancel(String id) { repository.cancel(id); }
}
