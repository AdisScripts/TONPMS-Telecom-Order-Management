package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.model.ProductStatus;
import com.amdocs.telecom.model.TelecomProduct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class TelecomProductDaoImpl extends AbstractJdbcDao implements TelecomProductDao {
    private static final String COLUMNS="product_id, product_code, product_name, product_type, description, monthly_price, activation_fee, contract_period, status, created_at, updated_at";
    public long save(final TelecomProduct p) { long id=insert("INSERT INTO telecom_product (product_code,product_name,product_type,description,monthly_price,activation_fee,contract_period,status) VALUES (?,?,?,?,?,?,?,?)", s->{s.setString(1,p.getProductCode());s.setString(2,p.getProductName());s.setString(3,p.getProductType());s.setString(4,p.getDescription());s.setBigDecimal(5,p.getMonthlyPrice());s.setBigDecimal(6,p.getActivationFee());s.setInt(7,p.getContractPeriod());s.setString(8,p.getStatus().name());});p.setProductId(id);return id; }
    public Optional<TelecomProduct> findById(Long id){return queryOne("SELECT "+COLUMNS+" FROM telecom_product WHERE product_id=?",s->s.setLong(1,id),this::mapRow);}
    public Optional<TelecomProduct> findByProductCode(String code){return queryOne("SELECT "+COLUMNS+" FROM telecom_product WHERE product_code=?",s->s.setString(1,code),this::mapRow);}
    public List<TelecomProduct> findAll(){return queryList("SELECT "+COLUMNS+" FROM telecom_product ORDER BY product_id",s->{},this::mapRow);}
    public List<TelecomProduct> findActiveProducts(){return queryList("SELECT "+COLUMNS+" FROM telecom_product WHERE status=? ORDER BY product_id",s->s.setString(1,ProductStatus.ACTIVE.name()),this::mapRow);}
    public boolean update(final TelecomProduct p){return executeUpdate("UPDATE telecom_product SET product_code=?,product_name=?,product_type=?,description=?,monthly_price=?,activation_fee=?,contract_period=?,status=? WHERE product_id=?",s->{s.setString(1,p.getProductCode());s.setString(2,p.getProductName());s.setString(3,p.getProductType());s.setString(4,p.getDescription());s.setBigDecimal(5,p.getMonthlyPrice());s.setBigDecimal(6,p.getActivationFee());s.setInt(7,p.getContractPeriod());s.setString(8,p.getStatus().name());s.setLong(9,p.getProductId());});}
    public boolean delete(Long id){return executeUpdate("DELETE FROM telecom_product WHERE product_id=?",s->s.setLong(1,id));}
    private TelecomProduct mapRow(ResultSet rs)throws SQLException{TelecomProduct p=new TelecomProduct();p.setProductId(rs.getLong("product_id"));p.setProductCode(rs.getString("product_code"));p.setProductName(rs.getString("product_name"));p.setProductType(rs.getString("product_type"));p.setDescription(rs.getString("description"));p.setMonthlyPrice(rs.getBigDecimal("monthly_price"));p.setActivationFee(rs.getBigDecimal("activation_fee"));p.setContractPeriod(rs.getInt("contract_period"));p.setStatus(ProductStatus.valueOf(rs.getString("status")));p.setCreatedAt(localDateTime(rs,"created_at"));p.setUpdatedAt(localDateTime(rs,"updated_at"));return p;}
}
