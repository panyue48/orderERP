package com.ordererp.backend.system.security;

import com.ordererp.backend.system.entity.SysUser;
import com.ordererp.backend.system.repository.SysMenuRepository;
import com.ordererp.backend.system.repository.SysUserRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class SysUserDetailsService implements UserDetailsService {
    private final SysUserRepository userRepository;
    private final SysMenuRepository menuRepository;

    public SysUserDetailsService(SysUserRepository userRepository, SysMenuRepository menuRepository) {
        this.userRepository = userRepository;
        this.menuRepository = menuRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userRepository.findByUsernameAndDeleted(username, 0)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        boolean enabled = user.getStatus() != null && user.getStatus() == 1;
        // 兼容旧的初始化数据（例如 erp_data.sql）：密码可能以明文 "123456" 的形式存储。
        // Spring Security 的 DelegatingPasswordEncoder 期望密码以 "{noop}" / "{bcrypt}" 等前缀声明算法。
        String storedPassword = user.getPassword();
        if (storedPassword != null && !storedPassword.startsWith("{")) {
            storedPassword = "{noop}" + storedPassword;
        }
        List<SimpleGrantedAuthority> authorities = menuRepository.findPermsByUserId(user.getId()).stream()
                .filter(perm -> perm != null && !perm.isBlank())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        return new SysUserDetails(user.getId(), user.getUsername(), storedPassword, user.getNickname(), enabled,
                authorities);
    }
}
