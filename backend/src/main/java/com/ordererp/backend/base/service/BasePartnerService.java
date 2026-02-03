package com.ordererp.backend.base.service;

import com.ordererp.backend.base.dto.PartnerCreateRequest;
import com.ordererp.backend.base.dto.PartnerOptionResponse;
import com.ordererp.backend.base.dto.PartnerResponse;
import com.ordererp.backend.base.dto.PartnerUpdateRequest;
import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BasePartnerService {
    private final BasePartnerRepository partnerRepository;

    public BasePartnerService(BasePartnerRepository partnerRepository) {
        this.partnerRepository = partnerRepository;
    }

    public Page<PartnerResponse> page(String keyword, Pageable pageable) {
        return partnerRepository.search(trimToNull(keyword), pageable).map(BasePartnerService::toResponse);
    }

    public PartnerResponse get(Long id) {
        BasePartner p = partnerRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "partner not found"));
        return toResponse(p);
    }

    public List<PartnerOptionResponse> options(String keyword, int limit) {
        int size = Math.min(Math.max(limit, 1), 200);
        Page<BasePartner> page = partnerRepository.options(trimToNull(keyword), PageRequest.of(0, size));
        return page.getContent().stream()
                .map(p -> new PartnerOptionResponse(p.getId(), p.getPartnerCode(), p.getPartnerName(), p.getType()))
                .toList();
    }

    @Transactional
    public PartnerResponse create(PartnerCreateRequest request) {
        String code = normalizeCode(request.partnerCode());
        BasePartner existing = partnerRepository.findFirstByPartnerCode(code).orElse(null);
        if (existing != null && (existing.getDeleted() == null || existing.getDeleted() == 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "partnerCode already exists");
        }

        int type = normalizeType(request.type());

        BasePartner p = existing != null ? existing : new BasePartner();
        if (p.getId() == null) {
            p.setCreateTime(LocalDateTime.now());
        }
        p.setPartnerName(normalizeName(request.partnerName()));
        p.setPartnerCode(code);
        p.setType(type);
        p.setContact(trimToNull(request.contact()));
        p.setPhone(trimToNull(request.phone()));
        p.setEmail(trimToNull(request.email()));
        p.setCreditLimit(request.creditLimit());
        p.setStatus(request.status() == null ? 1 : request.status());
        p.setDeleted(0);
        p.setUpdateTime(LocalDateTime.now());
        return toResponse(partnerRepository.save(p));
    }

    @Transactional
    public PartnerResponse update(Long id, PartnerUpdateRequest request) {
        BasePartner p = partnerRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "partner not found"));

        if (request.partnerCode() != null) {
            String code = normalizeCode(request.partnerCode());
            BasePartner other = partnerRepository.findFirstByPartnerCode(code).orElse(null);
            if (other != null && other.getId() != null && !other.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "partnerCode already exists");
            }
            p.setPartnerCode(code);
        }
        if (request.partnerName() != null) {
            p.setPartnerName(normalizeName(request.partnerName()));
        }
        if (request.type() != null) {
            p.setType(normalizeType(request.type()));
        }
        if (request.contact() != null) {
            p.setContact(trimToNull(request.contact()));
        }
        if (request.phone() != null) {
            p.setPhone(trimToNull(request.phone()));
        }
        if (request.email() != null) {
            p.setEmail(trimToNull(request.email()));
        }
        if (request.creditLimit() != null) {
            p.setCreditLimit(request.creditLimit());
        }
        if (request.status() != null) {
            p.setStatus(request.status());
        }
        p.setUpdateTime(LocalDateTime.now());
        return toResponse(partnerRepository.save(p));
    }

    @Transactional
    public void delete(Long id) {
        BasePartner p = partnerRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "partner not found"));
        p.setDeleted(1);
        p.setUpdateTime(LocalDateTime.now());
        partnerRepository.save(p);
    }

    private static PartnerResponse toResponse(BasePartner p) {
        return new PartnerResponse(
                p.getId(),
                p.getPartnerName(),
                p.getPartnerCode(),
                p.getType(),
                p.getContact(),
                p.getPhone(),
                p.getEmail(),
                p.getCreditLimit(),
                p.getStatus());
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private static String normalizeCode(String code) {
        String s = trimToNull(code);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "partnerCode is required");
        }
        return s;
    }

    private static String normalizeName(String name) {
        String s = trimToNull(name);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "partnerName is required");
        }
        return s;
    }

    private static int normalizeType(Integer type) {
        if (type == null || (type != 1 && type != 2)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be 1 or 2");
        }
        return type;
    }
}
