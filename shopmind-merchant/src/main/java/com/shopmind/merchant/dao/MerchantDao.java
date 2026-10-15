package com.shopmind.merchant.dao;

import com.shopmind.merchant.entity.MerchantDO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MerchantDao {

    private final JdbcTemplate jdbcTemplate;

    public MerchantDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 根据商家ID查询店铺信息及AI人设
     */
    public Optional<MerchantDO> findById(Long id) {
        String sql = "SELECT id, merchant_code, merchant_name, system_prompt, status FROM t_merchant WHERE id = ? AND status = 1";
        try {
            MerchantDO merchant = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new MerchantDO(
                    rs.getLong("id"),
                    rs.getString("merchant_code"),
                    rs.getString("merchant_name"),
                    rs.getString("system_prompt"),
                    rs.getInt("status")
            ), id);
            return Optional.ofNullable(merchant);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
